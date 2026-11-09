package com.cristian.moneymanager.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter. Enough for a single backend instance
 * (Render free plan); counters reset when the app restarts.
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final Duration MAX_WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** Records a hit and returns true if it is within the limit; returns false (and records nothing) otherwise. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        cleanupIfNeeded();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (deque) {
            evictOld(deque, now - window.toMillis());
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** True if the key already reached the limit within the window (does not record anything). */
    public boolean isLimited(String key, int limit, Duration window) {
        Deque<Long> deque = hits.get(key);
        if (deque == null) {
            return false;
        }
        synchronized (deque) {
            evictOld(deque, System.currentTimeMillis() - window.toMillis());
            return deque.size() >= limit;
        }
    }

    /** Records a hit without checking any limit (e.g. a failed login). */
    public void record(String key) {
        cleanupIfNeeded();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(System.currentTimeMillis());
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    /** Seconds until the oldest hit in the window expires (for the Retry-After header). */
    public long secondsUntilReset(String key, Duration window) {
        Deque<Long> deque = hits.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            Long oldest = deque.peekFirst();
            if (oldest == null) {
                return 0;
            }
            long millis = oldest + window.toMillis() - System.currentTimeMillis();
            return Math.max(1, (millis + 999) / 1000);
        }
    }

    private static void evictOld(Deque<Long> deque, long threshold) {
        while (!deque.isEmpty() && deque.peekFirst() <= threshold) {
            deque.pollFirst();
        }
    }

    private void cleanupIfNeeded() {
        if (hits.size() < CLEANUP_THRESHOLD) {
            return;
        }
        long threshold = System.currentTimeMillis() - MAX_WINDOW.toMillis();
        hits.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                evictOld(deque, threshold);
                return deque.isEmpty();
            }
        });
    }
}
