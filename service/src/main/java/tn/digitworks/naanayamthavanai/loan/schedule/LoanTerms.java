package tn.digitworks.naanayamthavanai.loan.schedule;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestCollection;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input for schedule generation.
 *
 * @param principal     loan amount
 * @param annualRatePct interest rate, % per annum (same unit as tenant max_interest_rate)
 * @param tenureCount   number of installments
 * @param startDate     disbursement date; first installment is due one period later
 */
public record LoanTerms(
        BigDecimal principal,
        BigDecimal annualRatePct,
        InterestMethod method,
        InterestCollection collection,
        Frequency frequency,
        int tenureCount,
        LocalDate startDate) {
}
