package tn.digitworks.naanayamthavanai.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Balances of one loan as of a day.
 *
 * @param outstanding   everything still collectible: open installment amounts + unpaid penalties
 * @param todayDue      open amount of installments due exactly on {@code asOf}
 * @param arrears       money behind schedule: open amount of past-due installments plus carried-forward
 *                      (extension) installments not yet due
 * @param arrearsCount  number of installments counted in arrears
 * @param penaltyDue    unpaid penalties
 */
public record LoanBalances(BigDecimal outstanding, BigDecimal todayDue, BigDecimal arrears, int arrearsCount,
                           BigDecimal penaltyDue) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public static LoanBalances of(List<Installment> installments, LocalDate asOf) {
        BigDecimal open = ZERO;
        BigDecimal today = ZERO;
        BigDecimal arrears = ZERO;
        BigDecimal penalty = ZERO;
        int arrearsCount = 0;

        for (Installment i : installments) {
            BigDecimal out = i.outstanding();
            penalty = penalty.add(i.penaltyOutstanding());
            if (out.signum() == 0) {
                continue;
            }
            open = open.add(out);
            if (i.getDueDate().isEqual(asOf)) {
                today = today.add(out);
            } else if (i.getDueDate().isBefore(asOf) || i.isExtension()) {
                arrears = arrears.add(out);
                arrearsCount++;
            }
        }
        return new LoanBalances(open.add(penalty), today, arrears, arrearsCount, penalty);
    }

    /** What an agent should try to collect now: today + arrears + penalty. */
    public BigDecimal totalDue() {
        return todayDue.add(arrears).add(penaltyDue);
    }
}
