package com.nkweb.pharma.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the sanitisation boundary.
 *
 * These tests are the executable specification of what may be stored: no markup, no control
 * characters, NFC normalised, truncated to the column width.
 */
class InputSanitizerTest {

    @Nested
    @DisplayName("markup removal")
    class MarkupRemoval {

        @Test
        void stripsTagsButKeepsVisibleText() {
            assertThat(InputSanitizer.text("<b>Jane</b> Doe", 80)).isEqualTo("Jane Doe");
            assertThat(InputSanitizer.text("<h1>Acme</h1> Ltd", 120)).isEqualTo("Acme Ltd");
        }

        @Test
        void removesScriptPayloads() {
            String cleaned = InputSanitizer.text("<script>alert('xss')</script>", 80);
            assertThat(cleaned).doesNotContain("<").doesNotContain(">");
        }

        @Test
        void removesEncodedMarkupThatBecomesLiveAfterEntityDecoding() {
            // &lt;img ...&gt; decodes to markup; the sanitiser must not let the brackets through.
            String cleaned = InputSanitizer.text("&lt;img src=x onerror=alert(1)&gt;", 80);
            assertThat(cleaned).doesNotContain("<").doesNotContain(">");
        }

        @Test
        void removesEventHandlerMarkupAndKeepsText() {
            String cleaned = InputSanitizer.multiline("<div onmouseover=\"steal()\">Hello</div>", 2000);
            assertThat(cleaned).isEqualTo("Hello");
        }
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        void stripsControlCharactersThatCouldForgeLogLines() {
            String cleaned = InputSanitizer.text("Jane\u0000Doe\r\nInjected: header", 80);
            assertThat(cleaned).doesNotContain("\r").doesNotContain("\n").doesNotContain("\u0000");
            assertThat(cleaned).isEqualTo("JaneDoe Injected: header");
        }

        @Test
        void normalisesUnicodeToNfc() {
            String decomposed = "Jose\u0301"; // e + combining acute accent
            assertThat(InputSanitizer.text(decomposed, 80)).isEqualTo("José");
        }

        @Test
        void collapsesWhitespaceAndTrims() {
            assertThat(InputSanitizer.text("   Acme   Pharma \t Ltd  ", 120)).isEqualTo("Acme Pharma Ltd");
        }

        @Test
        void collapsesNonBreakingSpaces() {
            assertThat(InputSanitizer.text("Acme\u00a0\u00a0Pharma", 120)).isEqualTo("Acme Pharma");
        }

        @Test
        void truncatesToTheColumnWidth() {
            String tooLong = "a".repeat(200);
            assertThat(InputSanitizer.text(tooLong, 80)).hasSize(80);
            // 200 characters fit inside the message column, so nothing is cut ...
            assertThat(InputSanitizer.multiline(tooLong, 2000)).hasSize(200);
            // ... whereas anything longer than the column is truncated to it.
            assertThat(InputSanitizer.multiline("a".repeat(2500), 2000)).hasSize(2000);
        }

        @Test
        void keepsLineBreaksInMultilineValuesButCollapsesBlankRuns() {
            String raw = "First line\n\n\n\nSecond line\n\nThird line";
            assertThat(InputSanitizer.multiline(raw, 2000))
                    .isEqualTo("First line\n\nSecond line\n\nThird line");
        }
    }

    @Nested
    @DisplayName("field specific rules")
    class FieldRules {

        @Test
        void lowerCasesAndCompactsEmailAddresses() {
            assertThat(InputSanitizer.email("  Jane.Doe@Example.COM  ")).isEqualTo("jane.doe@example.com");
            assertThat(InputSanitizer.email("jane\u00a0doe@example.com")).isEqualTo("janedoe@example.com");
        }

        @Test
        void keepsOnlyUsablePhoneCharacters() {
            assertThat(InputSanitizer.phone("+1 (555) 010-9999")).isEqualTo("+1 (555) 010-9999");
            // Letters are not valid phone input, so an injection attempt degrades to separators.
            assertThat(InputSanitizer.phone("555;DROP TABLE inquiries;--")).isEqualTo("555 --");
        }

        @Test
        void handlesNullAndEmptyInput() {
            assertThat(InputSanitizer.text(null, 80)).isNull();
            assertThat(InputSanitizer.multiline(null, 2000)).isNull();
            assertThat(InputSanitizer.email(null)).isNull();
            assertThat(InputSanitizer.phone(null)).isNull();
            assertThat(InputSanitizer.text("", 80)).isEmpty();
            assertThat(InputSanitizer.text("    ", 80)).isEmpty();
        }

        @Test
        void doesNotSplitSurrogatePairsWhenTruncating() {
            String emoji = "😀".repeat(60); // 120 chars, 60 code points
            String cleaned = InputSanitizer.text(emoji, 25);
            assertThat(cleaned).hasSize(24);
            assertThat(cleaned.codePointCount(0, cleaned.length())).isEqualTo(12);
        }
    }
}
