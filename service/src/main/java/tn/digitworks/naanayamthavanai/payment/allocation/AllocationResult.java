package tn.digitworks.naanayamthavanai.payment.allocation;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param allocations   rows for payment_allocation, in the order money was applied
 * @param updated       new state of every installment the payment touched
 * @param loanFullyPaid true when nothing is collectible any more (loan can move to CLOSED)
 */
public record AllocationResult(
        List<Allocation> allocations,
        List<InstallmentBalance> updated,
        BigDecimal installmentPart,
        BigDecimal penaltyPart,
        boolean loanFullyPaid) {

    /**
     * One payment_allocation row.
     *
     * @param amount      part applied to the installment amount (excludes penalty)
     * @param penaltyPart part applied to the installment's penalty
     */
    public record Allocation(long installmentId, BigDecimal amount, BigDecimal penaltyPart) {
    }
}
