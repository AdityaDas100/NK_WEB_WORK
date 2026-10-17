package com.nkweb.pharma.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token-bucket rate limiting for the public intake endpoint.
 *
 * @param enabled                  master switch.
 * @param requestsPerMinute        sustained requests allowed per client and per minute.
 * @param burstCapacity            extra requests a client may spend above the sustained rate.
 * @param globalRequestsPerMinute  aggregate ceiling protecting the service from a distributed
 *                                 flood from many distinct clients.
 * @param maxTrackedClients        hard cap on the number of client buckets held in memory; the
 *                                 oldest idle buckets are evicted first (see
 *                                 {@code SlidingWindowRateLimiter#evictStale}).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int requestsPerMinute,
        @DefaultValue("5") int burstCapacity,
        @DefaultValue("600") int globalRequestsPerMinute,
        @DefaultValue("100000") int maxTrackedClients) {

    public RateLimitProperties {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("app.rate-limit.requests-per-minute must be >= 1");
        }
        if (burstCapacity < 0) {
            throw new IllegalArgumentException("app.rate-limit.burst-capacity must be >= 0");
        }
    }
}
