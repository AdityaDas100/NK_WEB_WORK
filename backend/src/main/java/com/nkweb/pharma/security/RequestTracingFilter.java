package com.nkweb.pharma.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlates every log line and every response with a request identifier.
 *
 * <p>A client supplied {@code X-Request-Id} is accepted only when it matches a strict pattern, so a
 * hostile value cannot be used to inject newlines into the log stream or to poison the log index.
 * When it does not match, a fresh identifier is generated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_REQUEST_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = normaliseRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Returns a safe request id, generating one when the supplied value is missing or malformed. */
    public static String normaliseRequestId(String candidate) {
        if (candidate != null) {
            String trimmed = candidate.strip();
            if (!trimmed.isEmpty()
                    && trimmed.length() <= MAX_REQUEST_ID_LENGTH
                    && trimmed.matches("[A-Za-z0-9._:-]+")) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    /** The request id of the current thread, if any. Used by the exception handler. */
    public static String currentRequestId() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }
}
