package com.nkweb.pharma.security;

import com.nkweb.pharma.config.RateLimitProperties;
import com.nkweb.pharma.error.ProblemJson;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting for the public intake endpoint.
 *
 * <p>Three independent limits protect the service:
 * <ol>
 *   <li><b>sustained per client</b> – {@code app.rate-limit.requests-per-minute};</li>
 *   <li><b>burst per client</b> – at most {@code burst-capacity} submissions per second, which
 *       stops scripts that fire the whole minute quota at once;</li>
 *   <li><b>global</b> – {@code global-requests-per-minute} across all clients, so a botnet
 *       distributed over thousands of IPs still cannot exhaust the database pool.</li>
 * </ol>
 *
 * <p>Limiter keys are HMAC fingerprints of the client IP rather than the IP itself, so a heap dump
 * or a metric label never exposes a visitor's address. Denials are logged as a counter and a
 * single warning line without any PII.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String GUARDED_PATH = "/api/inquiries";
    private static final Duration SUSTAINED_WINDOW = Duration.ofMinutes(1);
    private static final Duration BURST_WINDOW = Duration.ofSeconds(1);
    private static final long CLEANUP_INTERVAL_REQUESTS = 500;

    private final RateLimitProperties properties;
    private final PiiMasker piiMasker;
    private final ClientIpResolver ipResolver;
    private final SlidingWindowRateLimiter sustainedLimiter;
    private final SlidingWindowRateLimiter burstLimiter;
    private final SlidingWindowRateLimiter globalLimiter;
    private final Counter rejected;
    private final Counter accepted;
    private final AtomicLong requestCounter = new AtomicLong();

    public RateLimitFilter(RateLimitProperties properties,
                           PiiMasker piiMasker,
                           ClientIpResolver ipResolver,
                           MeterRegistry meterRegistry) {
        this.properties = properties;
        this.piiMasker = piiMasker;
        this.ipResolver = ipResolver;
        this.sustainedLimiter = new SlidingWindowRateLimiter(
                properties.requestsPerMinute(), SUSTAINED_WINDOW, properties.maxTrackedClients());
        this.burstLimiter = new SlidingWindowRateLimiter(
                Math.max(1, properties.burstCapacity()), BURST_WINDOW, properties.maxTrackedClients());
        this.globalLimiter = new SlidingWindowRateLimiter(
                properties.globalRequestsPerMinute(), SUSTAINED_WINDOW, 16);
        this.rejected = meterRegistry.counter("pharma.inquiries.rate_limited");
        this.accepted = meterRegistry.counter("pharma.inquiries.allowed");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !GUARDED_PATH.equals(request.getRequestURI())
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        maybeCleanup();

        String clientKey = piiMasker.fingerprint(ipResolver.resolve(request));
        String method = request.getMethod();
        String path = request.getRequestURI();

        SlidingWindowRateLimiter.Decision burst = burstLimiter.tryAcquire(clientKey);
        if (!burst.allowed()) {
            reject(response, request, burst, "burst", clientKey, method, path);
            return;
        }

        SlidingWindowRateLimiter.Decision sustained = sustainedLimiter.tryAcquire(clientKey);
        if (!sustained.allowed()) {
            reject(response, request, sustained, "sustained", clientKey, method, path);
            return;
        }

        SlidingWindowRateLimiter.Decision global = globalLimiter.tryAcquire("global");
        if (!global.allowed()) {
            reject(response, request, global, "global", clientKey, method, path);
            return;
        }

        accepted.increment();
        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.requestsPerMinute()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(sustained.remaining()));
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpServletRequest request,
                        SlidingWindowRateLimiter.Decision decision, String scope,
                        String clientKey, String method, String path) throws IOException {

        rejected.increment();
        long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds() + 1);
        log.warn("rate_limit.rejected scope={} method={} path={} client_fp={} retry_after_s={}",
                scope, method, path, clientKey, retryAfterSeconds);

        ProblemJson.write(response, 429, "rate-limit-exceeded", "Too Many Requests",
                "Too many submissions from this client. Please wait "
                        + retryAfterSeconds + " seconds and try again.",
                request.getRequestURI(),
                java.util.Map.of("Retry-After", String.valueOf(retryAfterSeconds)));
    }

    private void maybeCleanup() {
        if (requestCounter.incrementAndGet() % CLEANUP_INTERVAL_REQUESTS == 0) {
            sustainedLimiter.evictStale();
            burstLimiter.evictStale();
        }
    }
}
