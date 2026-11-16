package tn.digitworks.naanayamthavanai.preclosure;

import tn.digitworks.naanayamthavanai.loan.missed.InstallmentRow;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings.ChargeType;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings.PreclosureRebate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Settlement amount to close a loan early. Pure logic.
 *
 * <pre>
 * settlement = outstandingPrincipal + outstandingInterest + pendingPenalty - interestRebate + charge
 * </pre>
 * <ul>
 *   <li>Outstanding amounts come from open (PENDING/PARTIAL) installments, split into principal and
 *       interest by each installment's own ratio.</li>
 *   <li>UNEARNED_INTEREST rebate = interest of installments due after {@code asOf}. Loans with
 *       interest deducted upfront have no interest in their installments, so their rebate is 0.</li>
 *   <li>PERCENT charge is on (principal + interest - rebate); penalties are not charged on.</li>
 * </ul>
 */
public final class PreclosureCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Quote(
            LocalDate asOf,
            BigDecimal outstandingPrincipal,
            BigDecimal outstandingInterest,
            BigDecimal pendingPenalty,
            BigDecimal interestRebate,
            BigDecimal preclosureCharge,
            BigDecimal settlementAmount,
            List<Settlement> settlements) {
    }

    /**
     * What each installment receives from the settlement payment.
     *
     * @param amount       installment part (outstanding minus rebate)
     * @param interestPart interest within amount
     */
    public record Settlement(long installmentId, BigDecimal amount, BigDecimal interestPart, BigDecimal penaltyPart) {
    }

    public Quote quote(List<InstallmentRow> installments, LocalDate asOf, PreclosureRebate rebate,
                       ChargeType chargeType, BigDecimal chargeValue) {
        BigDecimal principal = ZERO;
        BigDecimal interest = ZERO;
        BigDecimal penalty = ZERO;
        BigDecimal rebateTotal = ZERO;
        List<Settlement> settlements = new ArrayList<>();

        for (InstallmentRow r : installments) {
            if (r.status() == InstallmentStatus.SETTLED) {
                continue;
            }
            boolean open = r.status().isOpen();
            BigDecimal out = open ? r.dueAmount().subtract(r.paidAmount()) : ZERO;
            BigDecimal interestOut = interestShare(r, out);
            BigDecimal principalOut = out.subtract(interestOut);
            BigDecimal rebateHere = rebate == PreclosureRebate.UNEARNED_INTEREST && r.dueDate().isAfter(asOf)
                    ? interestOut : ZERO;
            BigDecimal penaltyHere = r.penaltyAmount().subtract(r.penaltyPaid());

            principal = principal.add(principalOut);
            interest = interest.add(interestOut);
            rebateTotal = rebateTotal.add(rebateHere);
            penalty = penalty.add(penaltyHere);

            if (out.signum() > 0 || penaltyHere.signum() > 0) {
                settlements.add(new Settlement(r.id(), out.subtract(rebateHere),
                        interestOut.subtract(rebateHere), penaltyHere));
            }
        }

        BigDecimal base = principal.add(interest).subtract(rebateTotal);
        BigDecimal charge = switch (chargeType == null ? ChargeType.NONE : chargeType) {
            case NONE -> ZERO;
            case FIXED -> chargeValue == null ? ZERO : chargeValue.setScale(2, RoundingMode.HALF_UP);
            case PERCENT -> chargeValue == null ? ZERO
                    : base.multiply(chargeValue).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        };

        return new Quote(asOf, principal, interest, penalty, rebateTotal, charge,
                base.add(penalty).add(charge), List.copyOf(settlements));
    }

    private static BigDecimal interestShare(InstallmentRow r, BigDecimal amount) {
        if (amount.signum() == 0 || r.dueAmount().signum() == 0) {
            return ZERO;
        }
        return amount.multiply(r.interestComponent()).divide(r.dueAmount(), 2, RoundingMode.HALF_UP);
    }
}
