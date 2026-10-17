package com.nkweb.pharma.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * CORS allow-list for the browser clients that are permitted to call the API directly.
 *
 * <p>Wildcards are deliberately not supported: the API is an unauthenticated public write
 * endpoint, so the set of origins allowed to send a cross-origin request must be explicit.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    public List<String> allowedOrigins() {
        return List.copyOf(allowedOrigins);
    }
}
