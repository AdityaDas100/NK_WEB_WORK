package com.nkweb.pharma.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Hard limits applied to the request body before it reaches the controller.
 *
 * <p>Rejecting oversized payloads early (413) is the cheapest protection against memory
 * exhaustion attacks: the JSON body is never buffered, parsed or validated.
 *
 * @param maxBodyBytes     maximum accepted {@code Content-Length} for the intake endpoint.
 * @param maxMessageLength maximum number of characters stored for the free-text message.
 */
@ConfigurationProperties(prefix = "app.request-limits")
public record RequestLimitProperties(
        @DefaultValue("16384") int maxBodyBytes,
        @DefaultValue("2000") int maxMessageLength) {

    public RequestLimitProperties {
        if (maxBodyBytes < 1024) {
            throw new IllegalArgumentException("app.request-limits.max-body-bytes must be >= 1024");
        }
        if (maxMessageLength < 100) {
            throw new IllegalArgumentException("app.request-limits.max-message-length must be >= 100");
        }
    }
}
