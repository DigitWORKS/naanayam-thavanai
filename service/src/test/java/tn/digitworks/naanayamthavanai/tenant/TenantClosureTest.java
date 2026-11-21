package tn.digitworks.naanayamthavanai.tenant;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantClosureTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private static Tenant tenant(Plan plan) {
        Tenant t = new Tenant();
        t.applyPlan(plan, TODAY);
        return t;
    }

    @Test
    void closeBlocksSignInAndSetsEraseDate() {
        Tenant t = tenant(Plan.YEARLY);
        t.close(LocalDateTime.of(2026, 11, 22, 23, 50));
        assertEquals(Tenant.Status.CLOSED, t.getStatus());
        assertEquals(LocalDate.of(2026, 12, 22), t.getEraseOn());
        assertFalse(t.isUsable(TODAY));
    }

    @Test
    void renewingAPlanDoesNotReopenAClosedBusiness() {
        Tenant t = tenant(Plan.TRIAL);
        t.close(TODAY.atStartOfDay());
        t.applyPlan(Plan.MONTHLY, TODAY);
        assertEquals(Tenant.Status.CLOSED, t.getStatus());
    }

    @Test
    void restoreFollowsThePlanAndClearsDates() {
        Tenant trial = tenant(Plan.TRIAL);
        trial.close(TODAY.atStartOfDay());
        trial.restore();
        assertEquals(Tenant.Status.TRIAL, trial.getStatus());
        assertNull(trial.getClosedAt());
        assertNull(trial.getEraseOn());
        assertTrue(trial.isUsable(TODAY));

        Tenant paid = tenant(Plan.YEARLY);
        paid.close(TODAY.atStartOfDay());
        paid.restore();
        assertEquals(Tenant.Status.ACTIVE, paid.getStatus());
    }

    @Test
    void onlyTrialAndActiveAreUsable() {
        assertTrue(Tenant.usable(Tenant.Status.TRIAL, TODAY, TODAY));
        assertTrue(Tenant.usable(Tenant.Status.ACTIVE, null, TODAY));
        assertFalse(Tenant.usable(Tenant.Status.ACTIVE, TODAY.minusDays(1), TODAY));
        assertFalse(Tenant.usable(Tenant.Status.SUSPENDED, null, TODAY));
        assertFalse(Tenant.usable(Tenant.Status.CLOSED, null, TODAY));
    }
}
