package tn.digitworks.naanayamthavanai.preclosure;

import tn.digitworks.naanayamthavanai.loan.missed.InstallmentRow;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;
import tn.digitworks.naanayamthavanai.preclosure.PreclosureCalculator.Quote;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings.ChargeType;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings.PreclosureRebate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.MISSED;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PAID;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PARTIAL;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PreclosureCalculatorTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 12, 1);
    private final PreclosureCalculator calc = new PreclosureCalculator();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** 5 daily installments of 120 (100 principal + 20 interest). */
    private static List<InstallmentRow> rows() {
        List<InstallmentRow> list = new ArrayList<>();
        for (int k = 1; k <= 5; k++) {
            list.add(row(k, PENDING, "0.00", "0.00", "0.00"));
        }
        return list;
    }

    private static InstallmentRow row(int k, InstallmentStatus s, String paid, String penalty, String penaltyPaid) {
        return new InstallmentRow((long) k, k, DAY1.plusDays(k - 1L), bd("120.00"), bd("100.00"), bd("20.00"),
                bd(paid), bd(penalty), bd(penaltyPaid), s, false, null);
    }

    @Test
    void noRebateNoCharge() {
        List<InstallmentRow> r = rows();
        r.set(0, row(1, PAID, "120.00", "0.00", "0.00"));

        Quote q = calc.quote(r, DAY1.plusDays(1), PreclosureRebate.NONE, ChargeType.NONE, null);

        assertEquals(bd("400.00"), q.outstandingPrincipal());
        assertEquals(bd("80.00"), q.outstandingInterest());
        assertEquals(bd("0.00"), q.interestRebate());
        assertEquals(bd("480.00"), q.settlementAmount());
        assertEquals(4, q.settlements().size());
    }

    @Test
    void unearnedInterestRebateForFutureInstallments() {
        // asOf = day 2: installments 3, 4, 5 are in the future -> 60 rebate
        Quote q = calc.quote(rows(), DAY1.plusDays(1), PreclosureRebate.UNEARNED_INTEREST, ChargeType.NONE, null);

        assertEquals(bd("60.00"), q.interestRebate());
        assertEquals(bd("540.00"), q.settlementAmount());   // 600 - 60
        PreclosureCalculator.Settlement future = q.settlements().get(4);
        assertEquals(bd("100.00"), future.amount());
        assertEquals(bd("0.00"), future.interestPart());
    }

    @Test
    void partialPaidAndPenaltiesIncluded() {
        List<InstallmentRow> r = rows();
        r.set(0, row(1, MISSED, "0.00", "10.00", "0.00"));      // shortfall moved, penalty 10
        r.set(1, row(2, PARTIAL, "60.00", "5.00", "5.00"));     // 60 left, penalty paid

        Quote q = calc.quote(r, DAY1.plusDays(1), PreclosureRebate.NONE, ChargeType.NONE, null);

        assertEquals(bd("10.00"), q.pendingPenalty());
        assertEquals(bd("50.00").add(bd("300.00")), q.outstandingPrincipal());   // 50 + 3x100
        assertEquals(bd("10.00").add(bd("60.00")), q.outstandingInterest());     // 10 + 3x20
        assertEquals(bd("430.00"), q.settlementAmount());                        // 420 + 10
        assertEquals(bd("10.00"), q.settlements().get(0).penaltyPart());
        assertEquals(bd("0.00"), q.settlements().get(0).amount());
    }

    @Test
    void percentChargeOnBaseExcludingPenalty() {
        List<InstallmentRow> r = rows();
        r.set(0, row(1, MISSED, "0.00", "10.00", "0.00"));

        Quote q = calc.quote(r, DAY1, PreclosureRebate.NONE, ChargeType.PERCENT, bd("2"));

        // base = 4 x 120 = 480 -> 2% = 9.60
        assertEquals(bd("9.60"), q.preclosureCharge());
        assertEquals(bd("499.60"), q.settlementAmount());   // 480 + 10 + 9.60
    }

    @Test
    void fixedCharge() {
        Quote q = calc.quote(rows(), DAY1, PreclosureRebate.NONE, ChargeType.FIXED, bd("50"));
        assertEquals(bd("650.00"), q.settlementAmount());
    }

    @Test
    void upfrontLoanHasNoRebate() {
        List<InstallmentRow> r = new ArrayList<>();
        for (int k = 1; k <= 3; k++) {
            r.add(new InstallmentRow((long) k, k, DAY1.plusDays(k - 1L), bd("100.00"), bd("100.00"), bd("0.00"),
                    bd("0.00"), bd("0.00"), bd("0.00"), PENDING, false, null));
        }
        Quote q = calc.quote(r, DAY1, PreclosureRebate.UNEARNED_INTEREST, ChargeType.NONE, null);
        assertEquals(bd("0.00"), q.interestRebate());
        assertEquals(bd("300.00"), q.settlementAmount());
    }

    @Test
    void settlementsAddUpToSettlementMinusCharge() {
        List<InstallmentRow> r = rows();
        r.set(1, row(2, PARTIAL, "33.33", "7.00", "0.00"));
        Quote q = calc.quote(r, DAY1.plusDays(2), PreclosureRebate.UNEARNED_INTEREST, ChargeType.FIXED, bd("25"));

        BigDecimal sum = q.settlements().stream()
                .map(s -> s.amount().add(s.penaltyPart())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.add(q.preclosureCharge()).compareTo(q.settlementAmount()));
    }
}
