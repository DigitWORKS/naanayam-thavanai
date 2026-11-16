package tn.digitworks.naanayamthavanai.payment.allocation;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Snapshot of one installment row, as needed for allocation. Immutable. */
public record InstallmentBalance(
        long id,
        int seqNo,
        LocalDate dueDate,
        BigDecimal dueAmount,
        BigDecimal paidAmount,
        BigDecimal penaltyAmount,
        BigDecimal penaltyPaid,
        InstallmentStatus status) {

    /** Collectible installment amount. Zero unless the installment is open. */
    public BigDecimal outstanding() {
        return status.isOpen() ? dueAmount.subtract(paidAmount) : BigDecimal.ZERO.setScale(2);
    }

    /** Penalty still collectible (on any status except SETTLED). */
    public BigDecimal penaltyOutstanding() {
        return status == InstallmentStatus.SETTLED
                ? BigDecimal.ZERO.setScale(2)
                : penaltyAmount.subtract(penaltyPaid);
    }
}
