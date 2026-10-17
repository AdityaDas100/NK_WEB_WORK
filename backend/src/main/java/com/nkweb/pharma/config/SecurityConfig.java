package com.nkweb.pharma.config;

import com.nkweb.pharma.error.ProblemJson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * HTTP hardening for the public API.
 *
 * <p>Decisions worth noting:
 * <ul>
 *   <li><b>Deny by default.</b> Only the intake endpoint and the health probes are reachable; every
 *       other path (including the rest of the actuator surface) answers {@code 403}.</li>
 *   <li><b>Stateless, no CSRF token.</b> The API never issues cookies and never authenticates a
 *       browser session, so there is no ambient authority for a cross-site request to abuse. Once
 *       cookie-based authentication is introduced, CSRF protection must be enabled again (see
 *       {@code docs/SECURITY.md}).</li>
 *   <li><b>Strict CORS.</b> Explicit origin allow-list, no credentials, short preflight cache.</li>
 *   <li><b>Security headers.</b> HSTS, {@code X-Content-Type-Options}, {@code X-Frame-Options},
 *       a restrictive {@code Content-Security-Policy} (the API never serves HTML), referrer and
 *       permissions policy.</li>
 *   <li><b>HTTPS.</b> {@code redirectToHttps} redirects plain-HTTP requests when
 *       {@code app.security.require-https} is set (enforced in the {@code prod} profile).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, AppSecurityProperties security)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(security.hstsMaxAgeSeconds()))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .crossOriginOpenerPolicy(coop -> coop
                                .policy(CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginResourcePolicy(corp -> corp
                                .policy(CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_SITE))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "accelerometer=(), camera=(), geolocation=(), gyroscope=(), "
                                        + "magnetometer=(), microphone=(), payment=(), usb=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Embedder-Policy", "require-corp")))
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight must never be blocked by authorization rules.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/inquiries").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        // Required so that Spring's error dispatch keeps the correct status code.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(
                                org.springframework.http.HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(forbiddenHandler()));

        if (security.requireHttps()) {
            // Spring Security 7 replaced `requiresChannel` (and ChannelProcessingFilter) with
            // `redirectToHttps`. The redirect honours X-Forwarded-Proto because the server runs with
            // `forward-headers-strategy: framework`, so it behaves correctly behind a TLS-terminating
            // proxy. Only enabled in production (APP_REQUIRE_HTTPS=true): local development is HTTP.
            http.redirectToHttps(Customizer.withDefaults());
        }
        return http.build();
    }

    /**
     * CORS for browser clients that talk to the API directly. The Next.js site can also be
     * deployed with its own server-side proxy, in which case this allow-list simply stays minimal.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of(HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(
                "Content-Type", "Accept", "Idempotency-Key", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static AccessDeniedHandler forbiddenHandler() {
        return (request, response, exception) -> ProblemJson.write(response, 403, "forbidden",
                "Forbidden",
                "This endpoint is not publicly available.",
                request.getRequestURI());
    }
}
