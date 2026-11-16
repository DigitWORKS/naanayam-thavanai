package tn.digitworks.naanayamthavanai.loan.schedule;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestCollection;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generates installment schedules. Pure logic: no Spring, no DB, thread-safe.
 *
 * <p>Rules:
 * <ul>
 *   <li>Rate is % per annum; interest uses actual days / 365.</li>
 *   <li>Installment i is due on startDate + i periods (Sundays/holidays included).</li>
 *   <li>Installments are rounded UP to the whole rupee; the last installment takes the
 *       remainder, so it is the same or smaller. Interest amount is rounded to the whole rupee.</li>
 *   <li>Tiny loans where whole-rupee rounding breaks the schedule fall back to paise.</li>
 *   <li>REDUCING + UPFRONT_DEDUCTED is rejected (interest depends on repayment timing).</li>
 * </ul>
 *
 * <p>Tenant-level checks (rate cap, allowed methods, active-loan limit) belong in LoanService.
 */
public final class ScheduleCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int MONEY_SCALE = 2;

    private enum Precision {
        RUPEE(0, RoundingMode.CEILING),
        PAISE(2, RoundingMode.HALF_UP);

        final int scale;
        final RoundingMode installmentRounding;

        Precision(int scale, RoundingMode installmentRounding) {
            this.scale = scale;
            this.installmentRounding = installmentRounding;
        }
    }

    public LoanSchedule calculate(LoanTerms terms) {
        validate(terms);
        return build(terms, Precision.RUPEE)
                .or(() -> build(terms, Precision.PAISE))
                .orElseThrow(() -> new ScheduleException("SCHEDULE_NOT_POSSIBLE",
                        "Loan amount is too small for the given tenure"));
    }

    private Optional<LoanSchedule> build(LoanTerms t, Precision p) {
        return t.method() == InterestMethod.FLAT ? flat(t, p) : reducing(t, p);
    }

    // ------------------------------------------------------------------ FLAT

    private Optional<LoanSchedule> flat(LoanTerms t, Precision p) {
        int n = t.tenureCount();
        BigDecimal principal = money(t.principal());
        BigDecimal totalDays = BigDecimal.valueOf((long) n * t.frequency().days());

        BigDecimal interest = principal
                .multiply(t.annualRatePct(), MC)
                .multiply(totalDays, MC)
                .divide(HUNDRED.multiply(DAYS_IN_YEAR), MC)
                .setScale(p.scale, RoundingMode.HALF_UP)
                .setScale(MONEY_SCALE);

        boolean upfront = t.collection() == InterestCollection.UPFRONT_DEDUCTED;
        BigDecimal disbursed = upfront ? principal.subtract(interest) : principal;
        BigDecimal totalPayable = upfront ? principal : principal.add(interest);
        BigDecimal interestInInstallments = upfront ? zero() : interest;

        if (disbursed.signum() <= 0) {
            throw new ScheduleException("INTEREST_EXCEEDS_PRINCIPAL",
                    "Upfront interest is equal to or more than the principal");
        }

        BigDecimal regular = totalPayable
                .divide(BigDecimal.valueOf(n), p.scale, p.installmentRounding)
                .setScale(MONEY_SCALE);
        BigDecimal last = totalPayable.subtract(regular.multiply(BigDecimal.valueOf(n - 1L)));
        if (last.signum() <= 0) {
            return Optional.empty();
        }

        List<LoanSchedule.Installment> rows = new ArrayList<>(n);
        BigDecimal balance = principal;
        BigDecimal interestSoFar = zero();

        for (int k = 1; k <= n; k++) {
            boolean isLast = k == n;
            BigDecimal due = isLast ? last : regular;
            BigDecimal interestPart = isLast
                    ? interestInInstallments.subtract(interestSoFar)
                    : due.multiply(interestInInstallments)
                         .divide(totalPayable, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal principalPart = due.subtract(interestPart);

            interestSoFar = interestSoFar.add(interestPart);
            balance = balance.subtract(principalPart);
            rows.add(new LoanSchedule.Installment(
                    k, dueDate(t, k), due, principalPart, interestPart, balance));
        }

        return Optional.of(new LoanSchedule(
                interest, disbursed, totalPayable, regular, dueDate(t, n), List.copyOf(rows)));
    }

    // -------------------------------------------------------------- REDUCING

    private Optional<LoanSchedule> reducing(LoanTerms t, Precision p) {
        int n = t.tenureCount();
        BigDecimal principal = money(t.principal());
        BigDecimal periodRate = t.annualRatePct()
                .multiply(BigDecimal.valueOf(t.frequency().days()), MC)
                .divide(HUNDRED.multiply(DAYS_IN_YEAR), MC);

        BigDecimal exactEmi;
        if (periodRate.signum() == 0) {
            exactEmi = principal.divide(BigDecimal.valueOf(n), MC);
        } else {
            BigDecimal growth = BigDecimal.ONE.add(periodRate).pow(n, MC);   // (1+i)^n
            exactEmi = principal.multiply(periodRate, MC).multiply(growth, MC)
                    .divide(growth.subtract(BigDecimal.ONE), MC);
        }
        BigDecimal emi = exactEmi.setScale(p.scale, p.installmentRounding).setScale(MONEY_SCALE);

        List<LoanSchedule.Installment> rows = new ArrayList<>(n);
        BigDecimal balance = principal;
        BigDecimal totalInterest = zero();

        for (int k = 1; k <= n; k++) {
            BigDecimal interestPart = balance.multiply(periodRate, MC)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal principalPart;
            BigDecimal due;

            if (k < n) {
                principalPart = emi.subtract(interestPart);
                if (principalPart.signum() <= 0 || principalPart.compareTo(balance) >= 0) {
                    return Optional.empty();   // rounding paid off too early or too late
                }
                due = emi;
            } else {
                principalPart = balance;
                due = principalPart.add(interestPart);
            }

            totalInterest = totalInterest.add(interestPart);
            balance = balance.subtract(principalPart);
            rows.add(new LoanSchedule.Installment(
                    k, dueDate(t, k), due, principalPart, interestPart, balance));
        }

        return Optional.of(new LoanSchedule(
                totalInterest, principal, principal.add(totalInterest), emi,
                dueDate(t, n), List.copyOf(rows)));
    }

    // ---------------------------------------------------------------- common

    private static void validate(LoanTerms t) {
        if (t == null || t.principal() == null || t.annualRatePct() == null || t.method() == null
                || t.collection() == null || t.frequency() == null || t.startDate() == null) {
            throw new ScheduleException("INVALID_TERMS", "All loan terms are required");
        }
        if (t.principal().signum() <= 0 || t.principal().stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new ScheduleException("INVALID_PRINCIPAL",
                    "Principal must be positive with at most 2 decimals");
        }
        if (t.annualRatePct().signum() < 0) {
            throw new ScheduleException("INVALID_INTEREST_RATE", "Interest rate cannot be negative");
        }
        if (t.tenureCount() < 1) {
            throw new ScheduleException("INVALID_TENURE", "Tenure must be at least 1 installment");
        }
        if (t.method() == InterestMethod.REDUCING
                && t.collection() == InterestCollection.UPFRONT_DEDUCTED) {
            throw new ScheduleException("UPFRONT_NOT_SUPPORTED_FOR_REDUCING",
                    "Upfront interest deduction is only supported for FLAT interest");
        }
    }

    private static LocalDate dueDate(LoanTerms t, int seqNo) {
        return t.startDate().plusDays((long) seqNo * t.frequency().days());
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
