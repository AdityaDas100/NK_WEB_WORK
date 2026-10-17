package com.nkweb.pharma.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SlidingWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void allowsUpToTheLimitThenDenies() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                3, Duration.ofMinutes(1), 100, clock);

        assertThat(limiter.tryAcquire("client").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client").allowed()).isTrue();
        SlidingWindowRateLimiter.Decision last = limiter.tryAcquire("client");
        assertThat(last.allowed()).isTrue();
        assertThat(last.remaining()).isZero();

        SlidingWindowRateLimiter.Decision denied = limiter.tryAcquire("client");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("a sliding window cannot be gamed by bursting at the boundary")
    void doesNotAllowDoubleTheRateAtAWindowBoundary() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                2, Duration.ofMinutes(1), 100, clock);

        limiter.tryAcquire("client");
        limiter.tryAcquire("client");

        // 1 ms before the window expires the full quota is still spent ...
        clock.advance(Duration.ofSeconds(59).plusMillis(999));
        assertThat(limiter.tryAcquire("client").allowed()).isFalse();

        // ... but once the timestamps fall out of the window the client is served again.
        clock.advance(Duration.ofMillis(2));
        assertThat(limiter.tryAcquire("client").allowed()).isTrue();
    }

    @Test
    void tracksClientsIndependently() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                1, Duration.ofMinutes(1), 100, clock);

        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("client-b").allowed()).isTrue();
        assertThat(limiter.trackedKeys()).isEqualTo(2);
    }

    @Test
    void evictsIdleBucketsSoMemoryStaysBounded() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                5, Duration.ofMinutes(1), 100, clock);

        for (int index = 0; index < 50; index++) {
            limiter.tryAcquire("client-" + index);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(50);

        clock.advance(Duration.ofMinutes(5));
        limiter.evictStale();

        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void dropsLeastRecentlyUsedBucketsWhenTheCapacityIsExceeded() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                5, Duration.ofMinutes(1), 10, clock);

        for (int index = 0; index < 10; index++) {
            limiter.tryAcquire("old-" + index);
            clock.advance(Duration.ofSeconds(1));
        }
        for (int index = 0; index < 5; index++) {
            limiter.tryAcquire("new-" + index);
        }

        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(10);
        assertThat(limiter.evictionCount()).isPositive();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SlidingWindowRateLimiter(0, Duration.ofMinutes(1), 10, clock));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SlidingWindowRateLimiter(1, Duration.ZERO, 10, clock));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SlidingWindowRateLimiter(1, Duration.ofMinutes(1), 0, clock));
    }

    /** Clock the test can move forward, which keeps the tests deterministic and instant. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
