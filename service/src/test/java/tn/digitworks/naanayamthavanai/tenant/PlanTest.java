package tn.digitworks.naanayamthavanai.tenant;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @Test
    void periodLengths() {
        assertEquals(LocalDate.of(2026, 12, 16), Plan.TRIAL.expiryFrom(TODAY));
        assertEquals(LocalDate.of(2026, 12, 16), Plan.MONTHLY.expiryFrom(TODAY));
        assertEquals(LocalDate.of(2027, 11, 16), Plan.YEARLY.expiryFrom(TODAY));
    }

    @Test
    void monthEndIsClamped() {
        assertEquals(LocalDate.of(2027, 2, 28), Plan.MONTHLY.expiryFrom(LocalDate.of(2027, 1, 31)));
    }

    @Test
    void newTenantStartsToday() {
        assertEquals(LocalDate.of(2026, 12, 16), Plan.TRIAL.renewedExpiry(null, TODAY));
    }

    @Test
    void renewalKeepsUnusedDays() {
        // trial ends 10 Dec; a monthly plan bought today runs from 10 Dec to 10 Jan
        assertEquals(LocalDate.of(2027, 1, 10), Plan.MONTHLY.renewedExpiry(LocalDate.of(2026, 12, 10), TODAY));
    }

    @Test
    void renewalOnTheLastDayExtendsFromThatDay() {
        assertEquals(LocalDate.of(2026, 12, 16), Plan.MONTHLY.renewedExpiry(TODAY, TODAY));
    }

    @Test
    void expiredTenantRestartsFromToday() {
        assertEquals(LocalDate.of(2027, 11, 16), Plan.YEARLY.renewedExpiry(LocalDate.of(2026, 10, 1), TODAY));
    }

    @Test
    void statusAndSelfServe() {
        assertEquals(Tenant.Status.TRIAL, Plan.TRIAL.status());
        assertEquals(Tenant.Status.ACTIVE, Plan.MONTHLY.status());
        assertEquals(Tenant.Status.ACTIVE, Plan.YEARLY.status());
        assertTrue(Plan.TRIAL.isSelfServe());
        assertFalse(Plan.MONTHLY.isSelfServe());
        assertFalse(Plan.YEARLY.isSelfServe());
    }
}
