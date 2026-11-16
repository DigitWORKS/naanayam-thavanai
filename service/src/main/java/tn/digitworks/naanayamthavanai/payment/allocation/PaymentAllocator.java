package tn.digitworks.naanayamthavanai.payment.allocation;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;
import tn.digitworks.naanayamthavanai.payment.allocation.AllocationResult.Allocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Splits a payment of any amount across a loan's installments. Pure logic, thread-safe.
 *
 * <p>Order of application:
 * <ol>
 *   <li>PENALTY_FIRST: outstanding penalties (oldest first), then open installments due on or
 *       before {@code asOf} (oldest first). INSTALLMENT_FIRST swaps these two.</li>
 *   <li>Remaining money is an advance on future open installments: next ones first
 *       (NEXT_INSTALLMENTS) or from the end of the schedule (LAST_INSTALLMENTS).</li>
 * </ol>
 *
 * <p>MISSED installments are never collected for their amount (it moved to an extension
 * installment), only for their penalty. Paying more than the total outstanding is rejected;
 * closing a loan early goes through pre-closure.
 *
 * <p>The caller passes ALL installments of one loan, loaded with a row lock
 * (SELECT ... FOR UPDATE) so two agents cannot allocate on the same loan at once.
 */
public final class PaymentAllocator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private static final Comparator<Working> OLDEST_FIRST =
            Comparator.comparing((Working w) -> w.dueDate).thenComparingInt(w -> w.seqNo);
    private static final Comparator<Working> BY_SEQ = Comparator.comparingInt(w -> w.seqNo);

    public AllocationResult allocate(List<InstallmentBalance> installments, BigDecimal amount,
                                     LocalDate asOf, AllocationPolicy policy) {
        validateAmount(amount);
        Map<Long, Working> rows = load(installments);

        BigDecimal totalOutstanding = rows.values().stream()
                .map(w -> w.outstanding().add(w.penaltyOutstanding()))
                .reduce(ZERO, BigDecimal::add);
        if (amount.compareTo(totalOutstanding) > 0) {
            throw new PaymentException("PAYMENT_EXCEEDS_OUTSTANDING",
                    "Payment " + amount + " is more than the outstanding " + totalOutstanding
                            + ". Use pre-closure to close the loan.");
        }

        Predicate<Working> dueNow = w -> w.status.isOpen() && !w.dueDate.isAfter(asOf);
        Predicate<Working> future = w -> w.status.isOpen() && w.dueDate.isAfter(asOf);
        Comparator<Working> advanceOrder = policy.advance() == AllocationPolicy.Advance.NEXT_INSTALLMENTS
                ? BY_SEQ : BY_SEQ.reversed();

        Ledger ledger = new Ledger();
        BigDecimal left = amount.setScale(2);
        if (policy.order() == AllocationPolicy.Order.PENALTY_FIRST) {
            left = payPenalties(rows, left, ledger);
            left = payInstallments(rows, dueNow, OLDEST_FIRST, left, ledger);
        } else {
            left = payInstallments(rows, dueNow, OLDEST_FIRST, left, ledger);
            left = payPenalties(rows, left, ledger);
        }
        left = payInstallments(rows, future, advanceOrder, left, ledger);

        if (left.signum() != 0) {
            throw new IllegalStateException("Unallocated amount " + left);   // guarded above
        }
        return ledger.result(rows);
    }

    /**
     * Undoes a payment's allocations (for a REVERSAL entry). Installments go back to
     * PENDING/PARTIAL; a past-due one that becomes open again is picked up by the
     * missed-installment job, which moves its shortfall to an extension as usual.
     */
    public AllocationResult reverse(List<InstallmentBalance> installments, List<Allocation> original) {
        Map<Long, Working> rows = load(installments);
        Ledger ledger = new Ledger();

        for (Allocation a : original) {
            Working w = rows.get(a.installmentId());
            if (w == null) {
                throw new PaymentException("INSTALLMENT_NOT_FOUND",
                        "Installment " + a.installmentId() + " is not part of this loan");
            }
            if (w.status == InstallmentStatus.SETTLED || w.status == InstallmentStatus.MISSED
                    && a.amount().signum() > 0) {
                throw new PaymentException("REVERSAL_NOT_ALLOWED",
                        "Installment " + w.seqNo + " is " + w.status + "; reverse the later entry first");
            }
            if (a.amount().compareTo(w.paidAmount) > 0 || a.penaltyPart().compareTo(w.penaltyPaid) > 0) {
                throw new PaymentException("REVERSAL_EXCEEDS_PAID",
                        "Reversal is more than what was paid on installment " + w.seqNo);
            }
            w.paidAmount = w.paidAmount.subtract(a.amount());
            w.penaltyPaid = w.penaltyPaid.subtract(a.penaltyPart());
            w.refreshOpenStatus();
            ledger.add(w, a.amount().negate(), a.penaltyPart().negate());
        }
        return ledger.result(rows);
    }

    // ------------------------------------------------------------------------

    private static BigDecimal payPenalties(Map<Long, Working> rows, BigDecimal left, Ledger ledger) {
        List<Working> targets = rows.values().stream()
                .filter(w -> w.penaltyOutstanding().signum() > 0)
                .sorted(OLDEST_FIRST)
                .toList();
        for (Working w : targets) {
            if (left.signum() == 0) {
                break;
            }
            BigDecimal pay = left.min(w.penaltyOutstanding());
            w.penaltyPaid = w.penaltyPaid.add(pay);
            ledger.add(w, ZERO, pay);
            left = left.subtract(pay);
        }
        return left;
    }

    private static BigDecimal payInstallments(Map<Long, Working> rows, Predicate<Working> filter,
                                              Comparator<Working> order, BigDecimal left, Ledger ledger) {
        List<Working> targets = rows.values().stream()
                .filter(filter)
                .filter(w -> w.outstanding().signum() > 0)
                .sorted(order)
                .toList();
        for (Working w : targets) {
            if (left.signum() == 0) {
                break;
            }
            BigDecimal pay = left.min(w.outstanding());
            w.paidAmount = w.paidAmount.add(pay);
            w.refreshOpenStatus();
            ledger.add(w, pay, ZERO);
            left = left.subtract(pay);
        }
        return left;
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new PaymentException("INVALID_AMOUNT", "Amount must be positive with at most 2 decimals");
        }
    }

    private static Map<Long, Working> load(List<InstallmentBalance> installments) {
        if (installments == null || installments.isEmpty()) {
            throw new PaymentException("NO_INSTALLMENTS", "Loan has no installments");
        }
        Map<Long, Working> rows = new LinkedHashMap<>();
        installments.forEach(i -> rows.put(i.id(), new Working(i)));
        return rows;
    }

    /** Mutable working copy of an installment. */
    private static final class Working {
        final long id;
        final int seqNo;
        final LocalDate dueDate;
        final BigDecimal dueAmount;
        final BigDecimal penaltyAmount;
        BigDecimal paidAmount;
        BigDecimal penaltyPaid;
        InstallmentStatus status;

        Working(InstallmentBalance b) {
            id = b.id();
            seqNo = b.seqNo();
            dueDate = b.dueDate();
            dueAmount = b.dueAmount().setScale(2);
            penaltyAmount = b.penaltyAmount().setScale(2);
            paidAmount = b.paidAmount().setScale(2);
            penaltyPaid = b.penaltyPaid().setScale(2);
            status = b.status();
        }

        BigDecimal outstanding() {
            return snapshot().outstanding();
        }

        BigDecimal penaltyOutstanding() {
            return snapshot().penaltyOutstanding();
        }

        /** Recomputes status for installments whose amount is still collectible. */
        void refreshOpenStatus() {
            if (status == InstallmentStatus.MISSED || status == InstallmentStatus.SETTLED) {
                return;
            }
            int cmp = paidAmount.compareTo(dueAmount);
            status = cmp >= 0 ? InstallmentStatus.PAID
                    : paidAmount.signum() > 0 ? InstallmentStatus.PARTIAL
                    : InstallmentStatus.PENDING;
        }

        InstallmentBalance snapshot() {
            return new InstallmentBalance(id, seqNo, dueDate, dueAmount, paidAmount,
                    penaltyAmount, penaltyPaid, status);
        }
    }

    /** Collects allocations, merging installment and penalty parts per installment. */
    private static final class Ledger {
        private final Map<Long, BigDecimal[]> parts = new LinkedHashMap<>();

        void add(Working w, BigDecimal amount, BigDecimal penalty) {
            BigDecimal[] p = parts.computeIfAbsent(w.id, k -> new BigDecimal[]{ZERO, ZERO});
            p[0] = p[0].add(amount);
            p[1] = p[1].add(penalty);
        }

        AllocationResult result(Map<Long, Working> rows) {
            List<Allocation> allocations = new ArrayList<>();
            List<InstallmentBalance> updated = new ArrayList<>();
            BigDecimal installmentPart = ZERO;
            BigDecimal penaltyPart = ZERO;

            for (Map.Entry<Long, BigDecimal[]> e : parts.entrySet()) {
                BigDecimal[] p = e.getValue();
                allocations.add(new Allocation(e.getKey(), p[0], p[1]));
                updated.add(rows.get(e.getKey()).snapshot());
                installmentPart = installmentPart.add(p[0]);
                penaltyPart = penaltyPart.add(p[1]);
            }

            boolean fullyPaid = rows.values().stream()
                    .allMatch(w -> w.outstanding().signum() == 0 && w.penaltyOutstanding().signum() == 0);
            return new AllocationResult(List.copyOf(allocations), List.copyOf(updated),
                    installmentPart, penaltyPart, fullyPaid);
        }
    }
}
