package tn.digitworks.naanayamthavanai.loan.schedule;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestCollection;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency.DAILY;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency.WEEKLY;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestCollection.ADDED_TO_REPAYMENT;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestCollection.UPFRONT_DEDUCTED;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod.FLAT;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod.REDUCING;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 1);
    private final ScheduleCalculator calc = new ScheduleCalculator();

    private static LoanTerms terms(String principal, String rate, InterestMethod m,
                                   InterestCollection c, Frequency f, int n) {
        return new LoanTerms(new BigDecimal(principal), new BigDecimal(rate), m, c, f, n, START);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** Invariants every schedule must satisfy. */
    private static void assertConsistent(LoanSchedule s, BigDecimal principal) {
        List<LoanSchedule.Installment> rows = s.installments();
        BigDecimal due = rows.stream().map(LoanSchedule.Installment::dueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prin = rows.stream().map(LoanSchedule.Installment::principalComponent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal intr = rows.stream().map(LoanSchedule.Installment::interestComponent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, due.compareTo(s.totalPayable()), "sum of dues = total payable");
        assertEquals(0, prin.compareTo(principal), "sum of principal parts = principal");
        assertEquals(0, prin.add(intr).compareTo(due), "principal + interest = due");
        assertEquals(0, rows.get(rows.size() - 1).principalBalanceAfter().signum(), "ends at zero");
        rows.forEach(r -> {
            assertTrue(r.dueAmount().signum() > 0, "positive due at seq " + r.seqNo());
            assertTrue(r.principalComponent().signum() >= 0, "non-negative principal at " + r.seqNo());
            assertTrue(r.interestComponent().signum() >= 0, "non-negative interest at " + r.seqNo());
        });
        assertEquals(s.endDate(), rows.get(rows.size() - 1).dueDate());
    }

    @Test
    void flatDailyAddedToRepayment() {
        // 10,000 at 20% p.a. for 100 days -> 547.95 -> 548
        LoanSchedule s = calc.calculate(terms("10000", "20", FLAT, ADDED_TO_REPAYMENT, DAILY, 100));

        assertEquals(bd("548.00"), s.interestAmount());
        assertEquals(bd("10000.00"), s.disbursedAmount());
        assertEquals(bd("10548.00"), s.totalPayable());
        assertEquals(bd("106.00"), s.installmentAmount());          // 105.48 rounded up
        assertEquals(100, s.installments().size());
        assertEquals(bd("54.00"), s.installments().get(99).dueAmount()); // 10548 - 99*106
        assertEquals(START.plusDays(1), s.installments().get(0).dueDate());
        assertEquals(START.plusDays(100), s.endDate());
        assertConsistent(s, bd("10000.00"));
    }

    @Test
    void flatDailyUpfrontDeducted() {
        LoanSchedule s = calc.calculate(terms("10000", "20", FLAT, UPFRONT_DEDUCTED, DAILY, 100));

        assertEquals(bd("548.00"), s.interestAmount());
        assertEquals(bd("9452.00"), s.disbursedAmount());
        assertEquals(bd("10000.00"), s.totalPayable());
        assertEquals(bd("100.00"), s.installmentAmount());
        s.installments().forEach(r -> {
            assertEquals(bd("100.00"), r.dueAmount());
            assertEquals(0, r.interestComponent().signum(), "interest already collected upfront");
        });
        assertConsistent(s, bd("10000.00"));
    }

    @Test
    void flatWeekly() {
        // 10,000 at 24% p.a. for 10 weeks (70 days) -> 460.27 -> 460
        LoanSchedule s = calc.calculate(terms("10000", "24", FLAT, ADDED_TO_REPAYMENT, WEEKLY, 10));

        assertEquals(bd("460.00"), s.interestAmount());
        assertEquals(bd("1046.00"), s.installmentAmount());
        s.installments().forEach(r -> assertEquals(bd("1046.00"), r.dueAmount()));
        assertEquals(START.plusDays(7), s.installments().get(0).dueDate());
        assertEquals(START.plusDays(70), s.endDate());
        assertConsistent(s, bd("10000.00"));
    }

    @Test
    void reducingWeekly() {
        LoanSchedule s = calc.calculate(terms("10000", "24", REDUCING, ADDED_TO_REPAYMENT, WEEKLY, 10));

        assertEquals(bd("10000.00"), s.disbursedAmount());
        assertTrue(s.interestAmount().signum() > 0);
        assertTrue(s.interestAmount().compareTo(bd("460.00")) < 0, "reducing < flat for same rate");
        // interest part falls as principal is repaid
        assertTrue(s.installments().get(0).interestComponent()
                .compareTo(s.installments().get(8).interestComponent()) > 0);
        assertTrue(s.installments().get(9).dueAmount().compareTo(s.installmentAmount()) <= 0);
        assertConsistent(s, bd("10000.00"));
    }

    @Test
    void reducingDailyLongTenure() {
        LoanSchedule s = calc.calculate(terms("50000", "36", REDUCING, ADDED_TO_REPAYMENT, DAILY, 200));
        assertEquals(200, s.installments().size());
        assertConsistent(s, bd("50000.00"));
    }

    @Test
    void zeroRate() {
        LoanSchedule flat = calc.calculate(terms("1000", "0", FLAT, ADDED_TO_REPAYMENT, DAILY, 10));
        LoanSchedule red = calc.calculate(terms("1000", "0", REDUCING, ADDED_TO_REPAYMENT, DAILY, 10));

        assertEquals(bd("0.00"), flat.interestAmount());
        assertEquals(bd("0.00"), red.interestAmount());
        assertEquals(bd("100.00"), flat.installmentAmount());
        assertEquals(bd("100.00"), red.installmentAmount());
        assertConsistent(flat, bd("1000.00"));
        assertConsistent(red, bd("1000.00"));
    }

    @Test
    void tinyLoanFallsBackToPaise() {
        // ceil(150/100) = 2 -> 99*2 > 150, so whole-rupee rounding is impossible
        LoanSchedule s = calc.calculate(terms("150", "0", FLAT, ADDED_TO_REPAYMENT, DAILY, 100));
        assertEquals(bd("1.50"), s.installmentAmount());
        assertConsistent(s, bd("150.00"));
    }

    @Test
    void singleInstallment() {
        LoanSchedule s = calc.calculate(terms("5000", "12", FLAT, ADDED_TO_REPAYMENT, WEEKLY, 1));
        assertEquals(1, s.installments().size());
        assertEquals(s.totalPayable(), s.installments().get(0).dueAmount());
        assertConsistent(s, bd("5000.00"));
    }

    @Test
    void rejectsReducingWithUpfront() {
        ScheduleException e = assertThrows(ScheduleException.class, () ->
                calc.calculate(terms("10000", "24", REDUCING, UPFRONT_DEDUCTED, WEEKLY, 10)));
        assertEquals("UPFRONT_NOT_SUPPORTED_FOR_REDUCING", e.getCode());
    }

    @Test
    void rejectsUpfrontInterestAbovePrincipal() {
        ScheduleException e = assertThrows(ScheduleException.class, () ->
                calc.calculate(terms("1000", "500", FLAT, UPFRONT_DEDUCTED, DAILY, 365)));
        assertEquals("INTEREST_EXCEEDS_PRINCIPAL", e.getCode());
    }

    @Test
    void rejectsInvalidInputs() {
        assertEquals("INVALID_PRINCIPAL", assertThrows(ScheduleException.class, () ->
                calc.calculate(terms("0", "10", FLAT, ADDED_TO_REPAYMENT, DAILY, 10))).getCode());
        assertEquals("INVALID_PRINCIPAL", assertThrows(ScheduleException.class, () ->
                calc.calculate(terms("100.555", "10", FLAT, ADDED_TO_REPAYMENT, DAILY, 10))).getCode());
        assertEquals("INVALID_INTEREST_RATE", assertThrows(ScheduleException.class, () ->
                calc.calculate(terms("1000", "-1", FLAT, ADDED_TO_REPAYMENT, DAILY, 10))).getCode());
        assertEquals("INVALID_TENURE", assertThrows(ScheduleException.class, () ->
                calc.calculate(terms("1000", "10", FLAT, ADDED_TO_REPAYMENT, DAILY, 0))).getCode());
    }
}
