package com.nkweb.pharma.security;

import com.nkweb.pharma.config.RequestLimitProperties;
import com.nkweb.pharma.error.ProblemJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects oversized submissions before a single byte of the body is buffered or parsed.
 *
 * <p>An attacker who can make the service allocate memory for a 1 GB JSON document can take the
 * whole instance down with a handful of requests, so the limit is enforced as early as possible
 * (this filter) and configured centrally in {@code app.request-limits.max-body-bytes}. A chunked
 * request without {@code Content-Length} is rejected as well, because it cannot be trusted to be
 * small; clients of this API always send a content length.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final String GUARDED_PATH = "/api/inquiries";

    private final int maxBodyBytes;

    public RequestSizeLimitFilter(RequestLimitProperties properties) {
        this.maxBodyBytes = properties.maxBodyBytes();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GUARDED_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            long declaredLength = request.getContentLengthLong();
            if (declaredLength < 0) {
                ProblemJson.write(response, 411, "length-required", "Length Required",
                        "A Content-Length header is required for inquiry submissions.",
                        request.getRequestURI());
                return;
            }
            if (declaredLength > maxBodyBytes) {
                ProblemJson.write(response, 413, "payload-too-large", "Payload Too Large",
                        "The submitted inquiry exceeds the maximum allowed size of "
                                + maxBodyBytes + " bytes.",
                        request.getRequestURI());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
