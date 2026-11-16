package tn.digitworks.naanayamthavanai.loan.missed;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Nightly job logic for one ACTIVE loan: applies the "missed payment extends the tenure" rule
 * and penalties. Pure logic, thread-safe, idempotent for the same {@code today}.
 *
 * <ol>
 *   <li>Every open (PENDING/PARTIAL) installment due before {@code today} is marked MISSED.
 *       Its shortfall is appended as a new extension installment one period after the current
 *       last due date. The interest/principal split of the shortfall keeps the ratio of the
 *       missed installment, so interest reports stay correct.</li>
 *   <li>A MISSED installment gets one penalty when it is more than {@code graceDays} late,
 *       was due on or after the policy's effectiveFrom, and its extension is still not fully
 *       paid. PERCENT is on the missed shortfall. The per-loan cap is applied.</li>
 * </ol>
 *
 * <p>Caller: run after midnight IST for each ACTIVE loan (skip DEFAULTED/CLOSED/PRECLOSED),
 * one transaction per loan, installments locked with SELECT ... FOR UPDATE.
 *
 * <p>Note: for REDUCING loans the extension carries the shortfall as-is; no extra interest
 * accrues on the delayed principal.
 */
public final class MissedInstallmentProcessor {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Comparator<Row> OLDEST_FIRST =
            Comparator.comparing((Row r) -> r.dueDate).thenComparingInt(r -> r.seqNo);

    public MissedProcessingResult process(List<InstallmentRow> installments, Frequency frequency,
                                          LocalDate today, PenaltyPolicy penalty) {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(penalty, "penalty");
        if (installments == null || installments.isEmpty()) {
            throw new IllegalArgumentException("Loan has no installments");
        }

        List<Row> rows = new ArrayList<>(installments.stream().map(Row::new).toList());
        Set<Row> changed = new LinkedHashSet<>();
        List<Row> created = new ArrayList<>();

        LocalDate lastDue = rows.stream().map(r -> r.dueDate).max(LocalDate::compareTo).orElseThrow();
        int nextSeq = rows.stream().mapToInt(r -> r.seqNo).max().orElseThrow() + 1;

        // 1. Mark missed days and append extensions
        List<Row> missedNow = rows.stream()
                .filter(r -> r.status.isOpen() && r.dueDate.isBefore(today))
                .sorted(OLDEST_FIRST)
                .toList();
        for (Row m : missedNow) {
            m.status = InstallmentStatus.MISSED;
            changed.add(m);

            lastDue = lastDue.plusDays(frequency.days());
            Row ext = extensionFor(m, nextSeq++, lastDue);
            rows.add(ext);
            created.add(ext);
        }

        // 2. Penalties
        BigDecimal penaltyAdded = ZERO;
        if (penalty.enabled()) {
            BigDecimal loanPenalty = rows.stream().map(r -> r.penaltyAmount).reduce(ZERO, BigDecimal::add);
            List<Row> candidates = rows.stream()
                    .filter(r -> r.status == InstallmentStatus.MISSED && r.penaltyAmount.signum() == 0)
                    .filter(r -> penalty.effectiveFrom() == null || !r.dueDate.isBefore(penalty.effectiveFrom()))
                    .filter(r -> ChronoUnit.DAYS.between(r.dueDate, today) > penalty.graceDays())
                    .filter(r -> extensionStillUnpaid(r, rows))
                    .sorted(OLDEST_FIRST)
                    .toList();

            for (Row m : candidates) {
                BigDecimal amount = penaltyFor(m, penalty);
                if (penalty.maxCapPerLoan() != null) {
                    amount = amount.min(penalty.maxCapPerLoan().subtract(loanPenalty).max(ZERO));
                }
                if (amount.signum() > 0) {
                    m.penaltyAmount = amount;
                    loanPenalty = loanPenalty.add(amount);
                    penaltyAdded = penaltyAdded.add(amount);
                    if (m.id != null) {
                        changed.add(m);
                    }
                }
            }
        }

        return new MissedProcessingResult(
                changed.stream().map(Row::toRecord).toList(),
                created.stream().map(Row::toRecord).toList(),
                lastDue,
                penaltyAdded);
    }

    private static Row extensionFor(Row missed, int seqNo, LocalDate dueDate) {
        BigDecimal shortfall = missed.dueAmount.subtract(missed.paidAmount);
        BigDecimal interest = missed.dueAmount.signum() == 0 ? ZERO
                : shortfall.multiply(missed.interestComponent)
                           .divide(missed.dueAmount, 2, RoundingMode.HALF_UP);
        return new Row(null, seqNo, dueDate, shortfall, shortfall.subtract(interest), interest,
                ZERO, ZERO, ZERO, InstallmentStatus.PENDING, true, missed.id);
    }

    /** True unless the extension carrying this missed shortfall has been fully paid. */
    private static boolean extensionStillUnpaid(Row missed, List<Row> rows) {
        if (missed.id == null) {
            return true;
        }
        return rows.stream()
                .filter(r -> missed.id.equals(r.extendedFrom))
                .findFirst()
                .map(ext -> ext.status != InstallmentStatus.PAID && ext.status != InstallmentStatus.SETTLED)
                .orElse(true);
    }

    private static BigDecimal penaltyFor(Row missed, PenaltyPolicy p) {
        if (p.type() == null || p.value() == null || p.value().signum() <= 0) {
            return ZERO;
        }
        return switch (p.type()) {
            case FIXED -> p.value().setScale(2, RoundingMode.HALF_UP);
            case PERCENT -> missed.dueAmount.subtract(missed.paidAmount)
                    .multiply(p.value())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        };
    }

    /** Mutable working copy. */
    private static final class Row {
        final Long id;
        final int seqNo;
        final LocalDate dueDate;
        final BigDecimal dueAmount;
        final BigDecimal principalComponent;
        final BigDecimal interestComponent;
        final BigDecimal paidAmount;
        BigDecimal penaltyAmount;
        final BigDecimal penaltyPaid;
        InstallmentStatus status;
        final boolean isExtension;
        final Long extendedFrom;

        Row(InstallmentRow r) {
            this(r.id(), r.seqNo(), r.dueDate(), r.dueAmount(), r.principalComponent(),
                    r.interestComponent(), r.paidAmount(), r.penaltyAmount(), r.penaltyPaid(),
                    r.status(), r.isExtension(), r.extendedFrom());
        }

        Row(Long id, int seqNo, LocalDate dueDate, BigDecimal dueAmount, BigDecimal principalComponent,
            BigDecimal interestComponent, BigDecimal paidAmount, BigDecimal penaltyAmount,
            BigDecimal penaltyPaid, InstallmentStatus status, boolean isExtension, Long extendedFrom) {
            this.id = id;
            this.seqNo = seqNo;
            this.dueDate = dueDate;
            this.dueAmount = dueAmount.setScale(2);
            this.principalComponent = principalComponent.setScale(2);
            this.interestComponent = interestComponent.setScale(2);
            this.paidAmount = paidAmount.setScale(2);
            this.penaltyAmount = penaltyAmount.setScale(2);
            this.penaltyPaid = penaltyPaid.setScale(2);
            this.status = status;
            this.isExtension = isExtension;
            this.extendedFrom = extendedFrom;
        }

        InstallmentRow toRecord() {
            return new InstallmentRow(id, seqNo, dueDate, dueAmount, principalComponent,
                    interestComponent, paidAmount, penaltyAmount, penaltyPaid, status,
                    isExtension, extendedFrom);
        }
    }
}
