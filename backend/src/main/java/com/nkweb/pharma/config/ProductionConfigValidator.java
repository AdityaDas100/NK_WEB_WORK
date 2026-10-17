package com.nkweb.pharma.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Fail-fast guard for production deployments.
 *
 * <p>Most secure defaults are useless when they are silently overridden by a placeholder, so the
 * application refuses to start with a development secret, an empty CORS list or a plaintext HTTP
 * configuration when the {@code prod} profile is active.
 */
@Configuration
@Profile("prod")
public class ProductionConfigValidator {

    private static final List<String> FORBIDDEN_SECRETS = List.of(
            "change-me-in-production-please-32-chars-min",
            "changeme",
            "secret");

    public ProductionConfigValidator(AppSecurityProperties security,
                                     CorsProperties cors,
                                     Environment environment) {
        if (environment.matchesProfiles("prod")) {
            validateSecret(security);
            validateCors(cors);
            validateHttps(security);
        }
    }

    private static void validateSecret(AppSecurityProperties security) {
        String secret = security.hashSecret() == null ? "" : security.hashSecret().trim();
        if (secret.length() < AppSecurityProperties.MIN_SECRET_LENGTH
                || FORBIDDEN_SECRETS.contains(secret.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalStateException("""
                    Refusing to start: app.security.hash-secret is a placeholder or too short.
                    Generate one with:  openssl rand -base64 48
                    and export it as APP_HASH_SECRET.""");
        }
    }

    private static void validateCors(CorsProperties cors) {
        if (cors.allowedOrigins().isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start: app.cors.allowed-origins must list the public site origin "
                            + "(set APP_CORS_ALLOWED_ORIGINS, comma separated).");
        }
        if (cors.allowedOrigins().stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException(
                    "Refusing to start: wildcard CORS origins are not allowed for the intake API.");
        }
    }

    private static void validateHttps(AppSecurityProperties security) {
        if (!security.requireHttps()) {
            throw new IllegalStateException(
                    "Refusing to start: app.security.require-https must be true in production.");
        }
    }
}
