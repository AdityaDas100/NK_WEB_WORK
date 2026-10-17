package com.nkweb.pharma.security;

import com.nkweb.pharma.config.AppSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Determines the real client IP address.
 *
 * <p>{@code X-Forwarded-For} is only consulted when {@code app.security.trust-proxy-headers} is
 * enabled, because the header is trivially spoofable by anyone who can reach the application
 * directly. When it is enabled the <em>first</em> (left-most) address is used, which is the
 * convention used by AWS ALB, Google Cloud Load Balancing and nginx configured with
 * {@code proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for}.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String REAL_IP = "X-Real-IP";

    private final boolean trustProxyHeaders;

    public ClientIpResolver(AppSecurityProperties properties) {
        this.trustProxyHeaders = properties.trustProxyHeaders();
    }

    public String resolve(HttpServletRequest request) {
        if (trustProxyHeaders) {
            Optional<String> forwarded = firstForwardedAddress(request.getHeader(FORWARDED_FOR));
            if (forwarded.isPresent()) {
                return forwarded.get();
            }
            String realIp = clean(request.getHeader(REAL_IP));
            if (realIp != null) {
                return realIp;
            }
        }
        String remote = clean(request.getRemoteAddr());
        return remote == null ? "unknown" : remote;
    }

    private static Optional<String> firstForwardedAddress(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        for (String candidate : header.split(",")) {
            String cleaned = clean(candidate);
            if (cleaned != null && isPlausibleAddress(cleaned)) {
                return Optional.of(cleaned);
            }
        }
        return Optional.empty();
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            return null;
        }
        // The value ends up in a metric tag / hash input: allow only characters that can appear in
        // an IP address or in a host name.
        return trimmed.matches("[0-9a-fA-F:.]{3,64}") ? trimmed : null;
    }

    private static boolean isPlausibleAddress(String value) {
        return value.matches("[0-9a-fA-F:.]{3,64}");
    }
}
