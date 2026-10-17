package com.nkweb.pharma.security;

import org.jsoup.Jsoup;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Single entry point for normalising untrusted text that originates from the public form.
 *
 * <p>Threat model and guarantees:
 * <ul>
 *   <li><b>Stored XSS / markup injection</b> – the payload is parsed as an HTML fragment and only
 *       its <em>text</em> is kept, so tags, event handlers and comments never survive. Any angle
 *       bracket that is left afterwards (for example from a decoded {@code &amp;lt;script&amp;gt;})
 *       is removed as well: values stored in the database contain no markup at all, which means
 *       even a completely unescaped consumer (CSV, Excel, e-mail, a future admin UI) cannot
 *       render them as HTML.</li>
 *   <li><b>Header/control character injection</b> – control characters are stripped, so values can
 *       never smuggle {@code CR/LF} into a log line or a spreadsheet cell.</li>
 *   <li><b>Homograph / encoding tricks</b> – input is Unicode NFC normalised before the length
 *       limits are applied, therefore a decomposed payload cannot bypass {@code varchar} limits.</li>
 *   <li><b>SQL injection</b> – <em>not</em> handled here. Every statement goes through JPA with
 *       bound parameters; string concatenation into SQL is forbidden by review (see
 *       {@code docs/SECURITY.md}).</li>
 * </ul>
 *
 * <p>The class is stateless and therefore thread-safe.
 */
public final class InputSanitizer {

    /** Control characters, except the ones that are meaningful inside a multi-line message. */
    private static final Pattern DISALLOWED_CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    /** Line-break characters: meaningful inside a message, never inside a single-line field. */
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n\\u0085\\u2028\\u2029]+");

    /** Everything that may legitimately appear in a phone number. */
    private static final Pattern PHONE_ALLOWED_CHARS = Pattern.compile("[^0-9+()\\-./ ]");

    /** Horizontal whitespace runs, collapsed into a single space. */
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[ \\t\\u00a0\\u2000-\\u200a\\u202f\\u205f\\u3000]+");

    /**
     * Every space-like, zero-width or BOM character. Needed for fields that must contain no
     * whitespace at all (e-mail), because {@code \s} does <em>not</em> match {@code U+00A0} and
     * the other Unicode spaces, which would otherwise survive into the stored address.
     */
    private static final Pattern ANY_WHITESPACE = Pattern.compile(
            "[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\u200b-\\u200d\\ufeff]+");

    private InputSanitizer() {
    }

    /**
     * Cleans a single-line value (name, company, city, ...): normalises, removes markup and
     * collapses every run of whitespace into a single space.
     */
    public static String text(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        // A single-line field must never keep a line break: that is how CR/LF header injection and
        // forged log lines are smuggled through a "name" field.
        String singleLine = LINE_BREAKS.matcher(stripMarkup(normalize(raw))).replaceAll(" ");
        return truncate(collapseWhitespace(singleLine), maxLength);
    }

    /**
     * Cleans a free-text value while keeping the author's line breaks (at most one blank line in a
     * row, so a hostile payload cannot produce 10 000 newlines).
     */
    public static String multiline(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String normalized = normalize(raw);
        StringBuilder cleaned = new StringBuilder(normalized.length());
        int emptyLinesInRow = 0;
        for (String line : normalized.split("\\R", -1)) {
            String candidate = collapseWhitespace(stripMarkup(line));
            if (candidate.isEmpty()) {
                emptyLinesInRow++;
                if (emptyLinesInRow > 1) {
                    continue;
                }
            } else {
                emptyLinesInRow = 0;
            }
            if (!cleaned.isEmpty()) {
                cleaned.append('\n');
            }
            cleaned.append(candidate);
        }
        String result = collapseWhitespace(cleaned.toString(), false).strip();
        return truncate(result, maxLength);
    }

    /**
     * Normalises an e-mail address: NFC, lower-cased, markup and all whitespace removed.
     * Format validation happens afterwards through Jakarta Bean Validation.
     */
    public static String email(String raw) {
        if (raw == null) {
            return null;
        }
        String value = ANY_WHITESPACE.matcher(normalize(raw)).replaceAll("");
        value = stripMarkup(value);
        return truncate(value.toLowerCase(Locale.ROOT), 254);
    }

    /**
     * Normalises a phone number: only digits and the characters {@code + ( ) - . / space} survive,
     * so a payload cannot use this field to inject anything into the export file.
     */
    public static String phone(String raw) {
        if (raw == null) {
            return null;
        }
        String value = PHONE_ALLOWED_CHARS.matcher(stripMarkup(normalize(raw))).replaceAll(" ");
        value = WHITESPACE_RUN.matcher(value).replaceAll(" ").strip();
        return truncate(value, 24);
    }

    /** Removes control characters and normalises the Unicode form. */
    private static String normalize(String raw) {
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC);
        return DISALLOWED_CONTROL_CHARS.matcher(value).replaceAll("");
    }

    /** Parses the value as an HTML fragment and keeps the plain text only. */
    private static String stripMarkup(String value) {
        if (value.isEmpty() || (value.indexOf('<') < 0 && value.indexOf('&') < 0)) {
            return value;
        }
        String text = Jsoup.parse(value).text();
        return text.replace('<', ' ').replace('>', ' ');
    }

    private static String collapseWhitespace(String value) {
        return collapseWhitespace(value, true);
    }

    private static String collapseWhitespace(String value, boolean collapseNewlines) {
        String collapsed = WHITESPACE_RUN.matcher(value).replaceAll(" ");
        if (!collapseNewlines) {
            return collapsed;
        }
        return collapsed.strip();
    }

    /** Truncates without splitting a surrogate pair in half. */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
