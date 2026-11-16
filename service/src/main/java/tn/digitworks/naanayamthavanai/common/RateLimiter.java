package tn.digitworks.naanayamthavanai.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limit per key (e.g. "register:&lt;ip&gt;"), kept in memory.
 * Single-instance only: with several servers, move this to a shared store (Redis) or the gateway.
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records an attempt; false when {@code max} attempts were already made within {@code window}. */
    public boolean tryAcquire(String key, int max, Duration window) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        if (hits.size() > MAX_KEYS) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Instant last = e.getValue().peekLast();
                    return last == null || last.isBefore(cutoff);
                }
            });
        }
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
