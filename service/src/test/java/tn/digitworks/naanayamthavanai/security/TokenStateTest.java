package tn.digitworks.naanayamthavanai.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenStateTest {

    @Test
    void currentVersionOfActiveUserIsAccepted() {
        assertTrue(TokenState.isCurrent(0, true, 0));
        assertTrue(TokenState.isCurrent(3, true, 3));
    }

    @Test
    void oldVersionIsRejected() {
        // PIN reset bumped the version: the old phone's token no longer works.
        assertFalse(TokenState.isCurrent(0, true, 1));
    }

    @Test
    void deactivatedUserIsRejected() {
        assertFalse(TokenState.isCurrent(1, false, 1));
    }

    @Test
    void suspendedOrExpiredFinancierIsRejected() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 19);
        assertTrue(TokenState.tenantUsable(null, null, today));                      // Super Admin
        assertTrue(TokenState.tenantUsable(tn.digitworks.naanayamthavanai.tenant.Tenant.Status.TRIAL, today, today));
        assertFalse(TokenState.tenantUsable(tn.digitworks.naanayamthavanai.tenant.Tenant.Status.ACTIVE,
                today.minusDays(1), today));
        assertFalse(TokenState.tenantUsable(tn.digitworks.naanayamthavanai.tenant.Tenant.Status.SUSPENDED,
                today.plusDays(30), today));
    }
}
