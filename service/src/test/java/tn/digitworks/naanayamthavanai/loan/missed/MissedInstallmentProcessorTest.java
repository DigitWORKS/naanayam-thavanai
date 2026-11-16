package tn.digitworks.naanayamthavanai.loan.missed;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency.DAILY;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency.WEEKLY;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.MISSED;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PAID;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PARTIAL;
import static tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus.PENDING;
import static org.junit.jupiter.api.Assertions.*;

class MissedInstallmentProcessorTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 12, 1);
    private final MissedInstallmentProcessor processor = new MissedInstallmentProcessor();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** n installments of 120 (100 principal + 20 interest), ids = seq = 1..n, every `step` days. */
    private static List<InstallmentRow> schedule(int n, int step) {
        List<InstallmentRow> list = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            list.add(new InstallmentRow((long) k, k, DAY1.plusDays((long) (k - 1) * step),
                    bd("120.00"), bd("100.00"), bd("20.00"), bd("0.00"), bd("0.00"), bd("0.00"),
                    PENDING, false, null));
        }
        return list;
    }

    private static InstallmentRow with(InstallmentRow r, String paid, InstallmentStatus status) {
        return new InstallmentRow(r.id(), r.seqNo(), r.dueDate(), r.dueAmount(), r.principalComponent(),
                r.interestComponent(), bd(paid), r.penaltyAmount(), r.penaltyPaid(), status,
                r.isExtension(), r.extendedFrom());
    }

    private static PenaltyPolicy fixed(String value, int grace) {
        return new PenaltyPolicy(true, PenaltyPolicy.Type.FIXED, bd(value), grace, null, null);
    }

    /** Applies a result to the rows, giving new extensions ids 1000, 1001, ... */
    private static List<InstallmentRow> apply(List<InstallmentRow> rows, MissedProcessingResult r) {
        List<InstallmentRow> out = new ArrayList<>(rows);
        r.updated().forEach(u -> out.replaceAll(x -> x.id().equals(u.id()) ? u : x));
        long nextId = 1000 + out.stream().filter(x -> x.id() >= 1000).count();
        for (InstallmentRow c : r.created()) {
            out.add(new InstallmentRow(nextId++, c.seqNo(), c.dueDate(), c.dueAmount(),
                    c.principalComponent(), c.interestComponent(), c.paidAmount(), c.penaltyAmount(),
                    c.penaltyPaid(), c.status(), c.isExtension(), c.extendedFrom()));
        }
        return out;
    }

    @Test
    void nothingToDoWhenPaidOrNotYetDue() {
        List<InstallmentRow> rows = new ArrayList<>(schedule(5, 1));
        rows.set(0, with(rows.get(0), "120.00", PAID));

        MissedProcessingResult r = processor.process(rows, DAILY, DAY1.plusDays(1), PenaltyPolicy.disabled());

        assertFalse(r.hasChanges());
        assertEquals(DAY1.plusDays(4), r.currentEndDate());
    }

    @Test
    void todayIsNotMissedYet() {
        MissedProcessingResult r = processor.process(schedule(5, 1), DAILY, DAY1, PenaltyPolicy.disabled());
        assertFalse(r.hasChanges());
    }

    @Test
    void unpaidDayIsMissedAndExtended() {
        MissedProcessingResult r = processor.process(schedule(5, 1), DAILY, DAY1.plusDays(1), PenaltyPolicy.disabled());

        assertEquals(1, r.updated().size());
        assertEquals(MISSED, r.updated().get(0).status());
        assertEquals(1, r.created().size());

        InstallmentRow ext = r.created().get(0);
        assertNull(ext.id());
        assertEquals(6, ext.seqNo());
        assertEquals(DAY1.plusDays(5), ext.dueDate());
        assertEquals(bd("120.00"), ext.dueAmount());
        assertEquals(bd("100.00"), ext.principalComponent());
        assertEquals(bd("20.00"), ext.interestComponent());
        assertTrue(ext.isExtension());
        assertEquals(1L, ext.extendedFrom());
        assertEquals(PENDING, ext.status());
        assertEquals(DAY1.plusDays(5), r.currentEndDate());
    }

    @Test
    void partialDayExtendsOnlyTheShortfall() {
        List<InstallmentRow> rows = new ArrayList<>(schedule(5, 1));
        rows.set(0, with(rows.get(0), "60.00", PARTIAL));

        MissedProcessingResult r = processor.process(rows, DAILY, DAY1.plusDays(1), PenaltyPolicy.disabled());

        InstallmentRow missed = r.updated().get(0);
        assertEquals(MISSED, missed.status());
        assertEquals(bd("60.00"), missed.paidAmount(), "paid part is kept");

        InstallmentRow ext = r.created().get(0);
        assertEquals(bd("60.00"), ext.dueAmount());
        assertEquals(bd("10.00"), ext.interestComponent(), "same 20/120 interest ratio");
        assertEquals(bd("50.00"), ext.principalComponent());
    }

    @Test
    void severalMissedDaysAreExtendedInOrder() {
        // Job did not run for 3 days
        MissedProcessingResult r = processor.process(schedule(5, 1), DAILY, DAY1.plusDays(3), PenaltyPolicy.disabled());

        assertEquals(3, r.updated().size());
        assertEquals(List.of(DAY1.plusDays(5), DAY1.plusDays(6), DAY1.plusDays(7)),
                r.created().stream().map(InstallmentRow::dueDate).toList());
        assertEquals(List.of(1L, 2L, 3L), r.created().stream().map(InstallmentRow::extendedFrom).toList());
        assertEquals(List.of(6, 7, 8), r.created().stream().map(InstallmentRow::seqNo).toList());
        assertEquals(DAY1.plusDays(7), r.currentEndDate());
    }

    @Test
    void weeklyExtensionIsOneWeekAfterLastDue() {
        MissedProcessingResult r = processor.process(schedule(4, 7), WEEKLY, DAY1.plusDays(1), PenaltyPolicy.disabled());
        assertEquals(DAY1.plusDays(28), r.created().get(0).dueDate());   // last due day 21 + 7
    }

    @Test
    void idempotentForSameDay() {
        List<InstallmentRow> rows = schedule(5, 1);
        LocalDate day2 = DAY1.plusDays(1);
        List<InstallmentRow> after = apply(rows, processor.process(rows, DAILY, day2, fixed("10", 0)));

        MissedProcessingResult again = processor.process(after, DAILY, day2, fixed("10", 0));
        assertFalse(again.hasChanges());
        assertEquals(bd("0.00"), again.penaltyAdded());
    }

    @Test
    void missedExtensionIsExtendedAgain() {
        List<InstallmentRow> rows = schedule(2, 1);
        List<InstallmentRow> after = apply(rows, processor.process(rows, DAILY, DAY1.plusDays(1), PenaltyPolicy.disabled()));
        // extension (id 1000) due day 3; on day 4 days 2 and 3 are both missed
        MissedProcessingResult r = processor.process(after, DAILY, DAY1.plusDays(3), PenaltyPolicy.disabled());

        assertEquals(List.of(2L, 1000L), r.created().stream().map(InstallmentRow::extendedFrom).toList());
    }

    @Test
    void fixedPenaltyWithoutGrace() {
        MissedProcessingResult r = processor.process(schedule(5, 1), DAILY, DAY1.plusDays(1), fixed("10", 0));

        assertEquals(bd("10.00"), r.updated().get(0).penaltyAmount());
        assertEquals(bd("10.00"), r.penaltyAdded());
    }

    @Test
    void graceDaysDelayPenalty() {
        List<InstallmentRow> rows = schedule(10, 1);
        PenaltyPolicy grace2 = fixed("10", 2);

        List<InstallmentRow> day2 = apply(rows, processor.process(rows, DAILY, DAY1.plusDays(1), grace2));
        assertEquals(bd("0.00"), day2.get(0).penaltyAmount(), "1 day late, within grace");

        // Day 4: day 1 is 3 days late -> penalty; days 2-3 still within grace
        MissedProcessingResult r = processor.process(day2, DAILY, DAY1.plusDays(3), grace2);
        List<InstallmentRow> day4 = apply(day2, r);
        assertEquals(bd("10.00"), day4.get(0).penaltyAmount());
        assertEquals(bd("0.00"), day4.get(1).penaltyAmount());
        assertEquals(bd("10.00"), r.penaltyAdded());
    }

    @Test
    void noPenaltyIfExtensionPaidWithinGrace() {
        List<InstallmentRow> rows = schedule(5, 1);
        PenaltyPolicy grace2 = fixed("10", 2);
        List<InstallmentRow> after = apply(rows, processor.process(rows, DAILY, DAY1.plusDays(1), grace2));

        // Client paid the extension (id 1000) the next day, e.g. via LAST_INSTALLMENTS advance
        after.replaceAll(x -> x.id() == 1000L ? with(x, "120.00", PAID) : x);

        MissedProcessingResult r = processor.process(after, DAILY, DAY1.plusDays(3), grace2);
        assertEquals(bd("0.00"), apply(after, r).get(0).penaltyAmount());
    }

    @Test
    void percentPenaltyIsOnShortfall() {
        List<InstallmentRow> rows = new ArrayList<>(schedule(5, 1));
        rows.set(0, with(rows.get(0), "20.00", PARTIAL));   // shortfall 100
        PenaltyPolicy pct = new PenaltyPolicy(true, PenaltyPolicy.Type.PERCENT, bd("2.5"), 0, null, null);

        MissedProcessingResult r = processor.process(rows, DAILY, DAY1.plusDays(1), pct);
        assertEquals(bd("2.50"), r.updated().get(0).penaltyAmount());
    }

    @Test
    void capLimitsTotalPenaltyPerLoan() {
        PenaltyPolicy capped = new PenaltyPolicy(true, PenaltyPolicy.Type.FIXED, bd("10"), 0, bd("25"), null);

        MissedProcessingResult r = processor.process(schedule(5, 1), DAILY, DAY1.plusDays(4), capped);

        assertEquals(bd("25.00"), r.penaltyAdded());
        assertEquals(List.of(bd("10.00"), bd("10.00"), bd("5.00")),
                r.updated().stream().map(InstallmentRow::penaltyAmount).filter(p -> p.signum() > 0).toList());
    }

    @Test
    void effectiveFromSkipsOlderMissedDays() {
        List<InstallmentRow> rows = schedule(5, 1);
        List<InstallmentRow> after = apply(rows, processor.process(rows, DAILY, DAY1.plusDays(2), PenaltyPolicy.disabled()));

        // Penalties turned on from day 2: day 1 (missed earlier) must not be charged
        PenaltyPolicy fromDay2 = new PenaltyPolicy(true, PenaltyPolicy.Type.FIXED, bd("10"), 0, null, DAY1.plusDays(1));
        MissedProcessingResult r = processor.process(after, DAILY, DAY1.plusDays(2), fromDay2);
        List<InstallmentRow> result = apply(after, r);

        assertEquals(bd("0.00"), result.get(0).penaltyAmount());
        assertEquals(bd("10.00"), result.get(1).penaltyAmount());
    }

    @Test
    void disabledPolicyAddsNoPenalty() {
        MissedProcessingResult r = processor.process(schedule(5, 1), DAILY, DAY1.plusDays(3), PenaltyPolicy.disabled());
        assertEquals(bd("0.00"), r.penaltyAdded());
        assertTrue(r.updated().stream().allMatch(u -> u.penaltyAmount().signum() == 0));
    }
}
