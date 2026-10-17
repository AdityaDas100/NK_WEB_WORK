package com.nkweb.pharma.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Security tuning knobs.
 *
 * @param hashSecret         secret used for HMAC pseudonymisation of PII in logs and for the
 *                           stored {@code source_ip_hash}. Must be a long random value in any
 *                           deployed environment ({@code openssl rand -base64 48}).
 * @param trustProxyHeaders  whether {@code X-Forwarded-For}/{@code -Proto} may be trusted. Only
 *                           enable when a proxy/LB you control terminates the connection.
 * @param requireHttps       when true every request must arrive over HTTPS (HTTP is redirected).
 * @param hstsMaxAgeSeconds  {@code Strict-Transport-Security} max-age.
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        @DefaultValue("change-me-in-production-please-32-chars-min") String hashSecret,
        @DefaultValue("false") boolean trustProxyHeaders,
        @DefaultValue("false") boolean requireHttps,
        @DefaultValue("31536000") long hstsMaxAgeSeconds) {

    /** Minimum length we accept for the PII hash secret. */
    public static final int MIN_SECRET_LENGTH = 32;

    public AppSecurityProperties {
        if (hashSecret == null || hashSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "app.security.hash-secret must be at least " + MIN_SECRET_LENGTH
                            + " characters long (set APP_HASH_SECRET)");
        }
    }
}
