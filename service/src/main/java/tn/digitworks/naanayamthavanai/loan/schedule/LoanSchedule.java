package tn.digitworks.naanayamthavanai.loan.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of schedule generation. Maps to loan + installment rows.
 *
 * @param installmentAmount the regular installment; the last one may be smaller (rounding remainder)
 */
public record LoanSchedule(
        BigDecimal interestAmount,
        BigDecimal disbursedAmount,
        BigDecimal totalPayable,
        BigDecimal installmentAmount,
        LocalDate endDate,
        List<Installment> installments) {

    /**
     * @param principalComponent    part of dueAmount that repays principal
     * @param interestComponent     part of dueAmount that is interest (0 for UPFRONT_DEDUCTED)
     * @param principalBalanceAfter outstanding principal after this installment is fully paid
     */
    public record Installment(
            int seqNo,
            LocalDate dueDate,
            BigDecimal dueAmount,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            BigDecimal principalBalanceAfter) {
    }
}
