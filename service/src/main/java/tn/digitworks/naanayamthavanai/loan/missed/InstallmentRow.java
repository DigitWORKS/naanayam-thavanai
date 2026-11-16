package tn.digitworks.naanayamthavanai.loan.missed;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One installment row as needed by the missed-installment job. Immutable.
 *
 * @param id           null for a new extension row not yet saved
 * @param extendedFrom id of the MISSED installment this extension carries forward
 */
public record InstallmentRow(
        Long id,
        int seqNo,
        LocalDate dueDate,
        BigDecimal dueAmount,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        BigDecimal paidAmount,
        BigDecimal penaltyAmount,
        BigDecimal penaltyPaid,
        InstallmentStatus status,
        boolean isExtension,
        Long extendedFrom) {

    /** Unpaid installment amount (ignores penalty). */
    public BigDecimal shortfall() {
        return dueAmount.subtract(paidAmount);
    }
}
