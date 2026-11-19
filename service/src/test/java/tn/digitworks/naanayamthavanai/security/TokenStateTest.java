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
}
