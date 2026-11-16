package tn.digitworks.naanayamthavanai.payment.allocation;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;
import tn.digitworks.naanayamthavanai.payment.allocation.AllocationPolicy.Advance;
import tn.digitworks.naanayamthavanai.payment.allocation.AllocationPolicy.Order;
import tn.digitworks.naanayamthavanai.payment.allocation.AllocationResult.Allocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.MISSED;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PAID;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PARTIAL;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PENDING;
import static org.junit.jupiter.api.Assertions.*;

class PaymentAllocatorTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 12, 1);
    private static final AllocationPolicy PENALTY_NEXT = new AllocationPolicy(Order.PENALTY_FIRST, Advance.NEXT_INSTALLMENTS);
    private static final AllocationPolicy INSTALLMENT_NEXT = new AllocationPolicy(Order.INSTALLMENT_FIRST, Advance.NEXT_INSTALLMENTS);
    private static final AllocationPolicy PENALTY_LAST = new AllocationPolicy(Order.PENALTY_FIRST, Advance.LAST_INSTALLMENTS);

    private final PaymentAllocator allocator = new PaymentAllocator();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** n daily installments of `due`, ids = seq = 1..n, due DAY1..DAY1+n-1, nothing paid. */
    private static List<InstallmentBalance> daily(int n, String due) {
        List<InstallmentBalance> list = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            list.add(new InstallmentBalance(k, k, DAY1.plusDays(k - 1L), bd(due), bd("0.00"),
                    bd("0.00"), bd("0.00"), PENDING));
        }
        return list;
    }

    private static InstallmentBalance with(InstallmentBalance i, String paid, String penalty,
                                           String penaltyPaid, InstallmentStatus status) {
        return new InstallmentBalance(i.id(), i.seqNo(), i.dueDate(), i.dueAmount(), bd(paid),
                bd(penalty), bd(penaltyPaid), status);
    }

    private static InstallmentBalance find(AllocationResult r, long id) {
        return r.updated().stream().filter(i -> i.id() == id).findFirst()
                .orElseThrow(() -> new AssertionError("installment " + id + " not updated"));
    }

    private static Allocation alloc(AllocationResult r, long id) {
        return r.allocations().stream().filter(a -> a.installmentId() == id).findFirst()
                .orElseThrow(() -> new AssertionError("no allocation for " + id));
    }

    private static void assertSums(AllocationResult r, String amount) {
        BigDecimal total = r.allocations().stream()
                .map(a -> a.amount().add(a.penaltyPart())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(bd(amount)), "allocations add up to the payment");
        assertEquals(0, r.installmentPart().add(r.penaltyPart()).compareTo(bd(amount)));
    }

    @Test
    void exactDueToday() {
        AllocationResult r = allocator.allocate(daily(10, "120.00"), bd("120"), DAY1, PENALTY_NEXT);

        assertEquals(1, r.allocations().size());
        assertEquals(PAID, find(r, 1).status());
        assertFalse(r.loanFullyPaid());
        assertSums(r, "120");
    }

    @Test
    void partialToday() {
        AllocationResult r = allocator.allocate(daily(10, "120.00"), bd("50"), DAY1, PENALTY_NEXT);

        assertEquals(PARTIAL, find(r, 1).status());
        assertEquals(bd("50.00"), find(r, 1).paidAmount());
        assertEquals(bd("70.00"), find(r, 1).outstanding());
    }

    @Test
    void overdueBeforeTodayIsPaidFirst() {
        // Day 1 unpaid and not yet marked MISSED; paying on day 2
        AllocationResult r = allocator.allocate(daily(10, "120.00"), bd("180"), DAY1.plusDays(1), PENALTY_NEXT);

        assertEquals(PAID, find(r, 1).status());
        assertEquals(PARTIAL, find(r, 2).status());
        assertEquals(bd("60.00"), find(r, 2).paidAmount());
        assertSums(r, "180");
    }

    @Test
    void advanceGoesToNextInstallments() {
        // 500 on day 1: day1 120, days 2-4 360, day 5 gets 20
        AllocationResult r = allocator.allocate(daily(10, "120.00"), bd("500"), DAY1, PENALTY_NEXT);

        for (long id = 1; id <= 4; id++) {
            assertEquals(PAID, find(r, id).status());
        }
        assertEquals(PARTIAL, find(r, 5).status());
        assertEquals(bd("20.00"), alloc(r, 5).amount());
        assertEquals(5, r.allocations().size());
        assertSums(r, "500");
    }

    @Test
    void advanceGoesToLastInstallments() {
        // 500 on day 1: day1 120, then days 10, 9, 8 (360), day 7 gets 20
        AllocationResult r = allocator.allocate(daily(10, "120.00"), bd("500"), DAY1, PENALTY_LAST);

        assertEquals(PAID, find(r, 1).status());
        assertEquals(PAID, find(r, 10).status());
        assertEquals(PAID, find(r, 9).status());
        assertEquals(PAID, find(r, 8).status());
        assertEquals(PARTIAL, find(r, 7).status());
        assertTrue(r.updated().stream().noneMatch(i -> i.id() == 2), "day 2 untouched");
        assertSums(r, "500");
    }

    @Test
    void missedAmountIsNotCollectedButPenaltyIs() {
        // Day 1 missed (shortfall moved to extension #11) with 10 penalty; paying on day 2
        List<InstallmentBalance> rows = new ArrayList<>(daily(10, "120.00"));
        rows.set(0, with(rows.get(0), "0.00", "10.00", "0.00", MISSED));
        rows.add(new InstallmentBalance(11, 11, DAY1.plusDays(10), bd("120.00"), bd("0.00"),
                bd("0.00"), bd("0.00"), PENDING));

        AllocationResult r = allocator.allocate(rows, bd("130"), DAY1.plusDays(1), PENALTY_NEXT);

        assertEquals(bd("0.00"), alloc(r, 1).amount());
        assertEquals(bd("10.00"), alloc(r, 1).penaltyPart());
        assertEquals(MISSED, find(r, 1).status(), "stays MISSED");
        assertEquals(PAID, find(r, 2).status());
        assertSums(r, "130");
    }

    @Test
    void lastInstallmentsAdvanceCancelsExtensionFirst() {
        List<InstallmentBalance> rows = new ArrayList<>(daily(3, "120.00"));
        rows.set(0, with(rows.get(0), "0.00", "0.00", "0.00", MISSED));
        rows.add(new InstallmentBalance(4, 4, DAY1.plusDays(3), bd("120.00"), bd("0.00"),
                bd("0.00"), bd("0.00"), PENDING));   // extension for day 1

        AllocationResult r = allocator.allocate(rows, bd("240"), DAY1.plusDays(1), PENALTY_LAST);

        assertEquals(PAID, find(r, 2).status());
        assertEquals(PAID, find(r, 4).status(), "extension paid, so loan ends on original date");
        assertTrue(r.updated().stream().noneMatch(i -> i.id() == 3));
    }

    @Test
    void penaltyFirstVsInstallmentFirst() {
        List<InstallmentBalance> rows = new ArrayList<>(daily(5, "120.00"));
        rows.set(0, with(rows.get(0), "0.00", "20.00", "0.00", MISSED));
        LocalDate day2 = DAY1.plusDays(1);

        AllocationResult penaltyFirst = allocator.allocate(rows, bd("100"), day2, PENALTY_NEXT);
        assertEquals(bd("20.00"), penaltyFirst.penaltyPart());
        assertEquals(bd("80.00"), penaltyFirst.installmentPart());

        AllocationResult installmentFirst = allocator.allocate(rows, bd("100"), day2, INSTALLMENT_NEXT);
        assertEquals(bd("0.00"), installmentFirst.penaltyPart());
        assertEquals(bd("100.00"), installmentFirst.installmentPart());

        // With enough money, INSTALLMENT_FIRST clears penalty before any advance
        AllocationResult more = allocator.allocate(rows, bd("150"), day2, INSTALLMENT_NEXT);
        assertEquals(bd("20.00"), more.penaltyPart());
        assertEquals(bd("10.00"), alloc(more, 3).amount());
    }

    @Test
    void paidAndSettledAreSkipped() {
        List<InstallmentBalance> rows = new ArrayList<>(daily(4, "100.00"));
        rows.set(0, with(rows.get(0), "100.00", "0.00", "0.00", PAID));
        rows.set(1, with(rows.get(1), "0.00", "0.00", "0.00", InstallmentStatus.SETTLED));

        AllocationResult r = allocator.allocate(rows, bd("100"), DAY1, PENALTY_NEXT);
        assertEquals(1, r.allocations().size());
        assertEquals(3L, r.allocations().get(0).installmentId());
    }

    @Test
    void exactOutstandingClosesLoan() {
        List<InstallmentBalance> rows = new ArrayList<>(daily(3, "100.00"));
        rows.set(0, with(rows.get(0), "40.00", "5.00", "0.00", PARTIAL));

        AllocationResult r = allocator.allocate(rows, bd("265"), DAY1, PENALTY_NEXT);  // 60+100+100+5
        assertTrue(r.loanFullyPaid());
        assertTrue(r.updated().stream().allMatch(i -> i.status() == PAID));
    }

    @Test
    void rejectsMoreThanOutstanding() {
        PaymentException e = assertThrows(PaymentException.class, () ->
                allocator.allocate(daily(3, "100.00"), bd("300.01"), DAY1, PENALTY_NEXT));
        assertEquals("PAYMENT_EXCEEDS_OUTSTANDING", e.getCode());
    }

    @Test
    void rejectsInvalidAmount() {
        List<InstallmentBalance> rows = daily(3, "100.00");
        assertEquals("INVALID_AMOUNT", assertThrows(PaymentException.class, () ->
                allocator.allocate(rows, bd("0"), DAY1, PENALTY_NEXT)).getCode());
        assertEquals("INVALID_AMOUNT", assertThrows(PaymentException.class, () ->
                allocator.allocate(rows, bd("-5"), DAY1, PENALTY_NEXT)).getCode());
        assertEquals("INVALID_AMOUNT", assertThrows(PaymentException.class, () ->
                allocator.allocate(rows, bd("10.005"), DAY1, PENALTY_NEXT)).getCode());
    }

    @Test
    void reversalRestoresState() {
        List<InstallmentBalance> rows = new ArrayList<>(daily(5, "120.00"));
        rows.set(0, with(rows.get(0), "0.00", "10.00", "0.00", MISSED));
        LocalDate day2 = DAY1.plusDays(1);

        AllocationResult paid = allocator.allocate(rows, bd("200"), day2, PENALTY_NEXT);

        // Apply the result, then reverse it
        List<InstallmentBalance> afterPay = new ArrayList<>(rows);
        paid.updated().forEach(u -> afterPay.replaceAll(i -> i.id() == u.id() ? u : i));
        AllocationResult reversed = allocator.reverse(afterPay, paid.allocations());

        for (InstallmentBalance u : reversed.updated()) {
            InstallmentBalance original = rows.stream().filter(i -> i.id() == u.id()).findFirst().orElseThrow();
            assertEquals(original, u, "installment " + u.id() + " restored");
        }
        assertEquals(paid.installmentPart().negate(), reversed.installmentPart());
        assertEquals(paid.penaltyPart().negate(), reversed.penaltyPart());
    }

    @Test
    void reversalRejectsMoreThanPaid() {
        List<InstallmentBalance> rows = daily(3, "100.00");
        PaymentException e = assertThrows(PaymentException.class, () ->
                allocator.reverse(rows, List.of(new Allocation(1, bd("50.00"), bd("0.00")))));
        assertEquals("REVERSAL_EXCEEDS_PAID", e.getCode());
    }
}
