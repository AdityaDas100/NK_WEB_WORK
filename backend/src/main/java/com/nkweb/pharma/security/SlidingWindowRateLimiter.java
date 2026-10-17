package com.nkweb.pharma.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory sliding-window counter.
 *
 * <p>Why a sliding window instead of a fixed one: a fixed window lets a client send the full quota
 * at {@code 59.9s} and again at {@code 60.1s}, i.e. twice the intended rate in a 200 ms burst. The
 * sliding window keeps the exact timestamps of the accepted requests, which gives a hard bound on
 * any interval of length {@code window}.
 *
 * <p><b>Scope.</b> State lives in the JVM, so the limiter is per instance. That is the right
 * trade-off for the common single-service deployment, and it is a deliberate, documented upgrade
 * path for horizontal scaling:
 * <ul>
 *   <li>edge: enforce a coarse limit in the ingress controller / WAF / CDN for every instance;</li>
 *   <li>or replace this class by a Redis-backed counter ({@code INCR} + {@code EXPIRE} or a
 *       Bucket4j {@code RedisBackend}) keeping the same {@link Decision} contract.</li>
 * </ul>
 *
 * <p>Memory is bounded: buckets idle for longer than the window are dropped lazily, and the map is
 * never allowed to exceed the configured capacity (the oldest buckets are evicted first).
 * Thread-safe.
 */
public final class SlidingWindowRateLimiter {

    /** Result of a single permit attempt. */
    public record Decision(boolean allowed, long remaining, Duration retryAfter) {

        public static Decision allow(long remaining) {
            return new Decision(true, remaining, Duration.ZERO);
        }

        public static Decision deny(Duration retryAfter) {
            return new Decision(false, 0, retryAfter);
        }
    }

    private final int limit;
    private final long windowMillis;
    private final int maxTrackedKeys;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong evictions = new AtomicLong();

    public SlidingWindowRateLimiter(int limit, Duration window, int maxTrackedKeys) {
        this(limit, window, maxTrackedKeys, Clock.systemUTC());
    }

    SlidingWindowRateLimiter(int limit, Duration window, int maxTrackedKeys, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (maxTrackedKeys < 1) {
            throw new IllegalArgumentException("maxTrackedKeys must be >= 1");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = clock;
    }

    /**
     * Consumes one permit for {@code key}.
     *
     * @return whether the request is allowed, how many permits are left, and – when it is denied –
     *         how long the client should wait before retrying.
     */
    public Decision tryAcquire(String key) {
        long now = clock.millis();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            // A brand-new key: make room for it *before* it is inserted, otherwise the map can
            // legitimately reach maxTrackedKeys + 1 entries (evict-to-capacity, then insert).
            makeRoom(now);
            bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        }
        synchronized (bucket) {
            bucket.dropOlderThan(now - windowMillis);
            bucket.lastSeen = now;
            if (bucket.timestamps.size() >= limit) {
                Long oldest = bucket.timestamps.peekFirst();
                long waitMillis = oldest == null ? windowMillis : (oldest + windowMillis) - now;
                return Decision.deny(Duration.ofMillis(Math.max(1, waitMillis)));
            }
            bucket.timestamps.addLast(now);
            return Decision.allow(limit - bucket.timestamps.size());
        }
    }

    /** Drops buckets that have been idle for at least two windows. Safe to call at any time. */
    public void evictStale() {
        evictStale(clock.millis());
    }

    private void evictStale(long now) {
        long idleThreshold = now - (windowMillis * 2);
        buckets.entrySet().removeIf(entry -> entry.getValue().lastSeen < idleThreshold);
        if (buckets.size() > maxTrackedKeys) {
            evictOldest(buckets.size() - maxTrackedKeys);
        }
    }

    /** Frees one slot so a brand-new key can be tracked without exceeding {@code maxTrackedKeys}. */
    private void makeRoom(long now) {
        if (buckets.size() < maxTrackedKeys) {
            return;
        }
        evictStale(now);
        int excess = buckets.size() - maxTrackedKeys + 1;
        if (excess > 0) {
            evictOldest(excess);
        }
    }

    /** Last-resort bound on memory: removes the {@code excess} least recently used buckets. */
    private void evictOldest(int excess) {
        List<Map.Entry<String, Bucket>> snapshot = buckets.entrySet()
                .stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastSeen))
                .limit(excess)
                .toList();
        snapshot.forEach(entry -> {
            buckets.remove(entry.getKey(), entry.getValue());
            evictions.incrementAndGet();
        });
    }

    public int trackedKeys() {
        return buckets.size();
    }

    public long evictionCount() {
        return evictions.get();
    }

    public int limit() {
        return limit;
    }

    public Duration window() {
        return Duration.ofMillis(windowMillis);
    }

    private static final class Bucket {

        private final Deque<Long> timestamps = new ArrayDeque<>();
        private volatile long lastSeen;

        void dropOlderThan(long cutoff) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() != null
                    && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
        }
    }
}
