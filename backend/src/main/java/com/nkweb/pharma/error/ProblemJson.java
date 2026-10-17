package com.nkweb.pharma.error;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal RFC 9457 ({@code application/problem+json}) writer for the places where the response is
 * produced by a servlet filter or a Spring Security handler, i.e. before the Spring MVC exception
 * handlers are involved.
 *
 * <p>JSON is written by hand on purpose: these paths must not depend on the configured message
 * converters, and no user supplied value is ever emitted unescaped.
 */
public final class ProblemJson {

    public static final String MEDIA_TYPE = "application/problem+json";
    private static final String TYPE_BASE = "https://errors.nkethical.example/";

    private ProblemJson() {
    }

    public static void write(HttpServletResponse response, int status, String code, String title,
                             String detail, String instance) throws IOException {
        write(response, status, code, title, detail, instance, Map.of());
    }

    public static void write(HttpServletResponse response, int status, String code, String title,
                             String detail, String instance, Map<String, String> headers)
            throws IOException {

        if (!response.isCommitted()) {
            response.reset();
            response.setStatus(status);
            response.setContentType(MEDIA_TYPE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-store");
            headers.forEach(response::setHeader);
        }

        StringBuilder json = new StringBuilder(256)
                .append('{')
                .append("\"type\":").append(quote(TYPE_BASE + code)).append(',')
                .append("\"title\":").append(quote(title)).append(',')
                .append("\"status\":").append(status).append(',')
                .append("\"detail\":").append(quote(detail)).append(',')
                .append("\"code\":").append(quote(code)).append(',')
                .append("\"instance\":").append(quote(instance))
                .append('}');

        response.getWriter().write(json.toString());
        response.flushBuffer();
    }

    /** Escapes a value so that it can be embedded into a JSON string literal. */
    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
