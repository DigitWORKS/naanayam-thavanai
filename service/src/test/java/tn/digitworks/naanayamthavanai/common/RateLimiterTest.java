package tn.digitworks.naanayamthavanai.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    /** A clock the test moves forward. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-16T06:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Kolkata"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final Duration HOUR = Duration.ofHours(1);

    @Test
    void allowsUpToMaxThenBlocks() {
        RateLimiter limiter = new RateLimiter(new TestClock());
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("register:1.2.3.4", 3, HOUR));
        }
        assertFalse(limiter.tryAcquire("register:1.2.3.4", 3, HOUR));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(new TestClock());
        assertTrue(limiter.tryAcquire("register:a", 1, HOUR));
        assertFalse(limiter.tryAcquire("register:a", 1, HOUR));
        assertTrue(limiter.tryAcquire("register:b", 1, HOUR));
    }

    @Test
    void windowSlides() {
        TestClock clock = new TestClock();
        RateLimiter limiter = new RateLimiter(clock);
        assertTrue(limiter.tryAcquire("k", 2, HOUR));
        clock.advance(Duration.ofMinutes(40));
        assertTrue(limiter.tryAcquire("k", 2, HOUR));
        assertFalse(limiter.tryAcquire("k", 2, HOUR));
        clock.advance(Duration.ofMinutes(21));   // the first attempt is now over an hour old
        assertTrue(limiter.tryAcquire("k", 2, HOUR));
        assertFalse(limiter.tryAcquire("k", 2, HOUR));
    }
}
