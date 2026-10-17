package com.nkweb.pharma.service.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spreadsheet formula injection ("CSV injection") protection.
 *
 * A web form that ends up in a spreadsheet is a classic pivot into the reviewer's workstation, so the
 * neutralisation is tested exhaustively for the bypass variants that spreadsheet applications accept.
 */
class SpreadsheetSafetyTest {

    @Test
    @DisplayName("prefixes an apostrophe so the cell is plain text")
    void prefixesFormulaTriggers() {
        assertThat(SpreadsheetSafety.defuse("=SUM(A1:A2)")).isEqualTo("'=SUM(A1:A2)");
        assertThat(SpreadsheetSafety.defuse("+1234")).isEqualTo("'+1234");
        assertThat(SpreadsheetSafety.defuse("-2+3")).isEqualTo("'-2+3");
        assertThat(SpreadsheetSafety.defuse("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    @DisplayName("strips the control characters used to hide the formula marker")
    void stripsLeadingControlCharactersBeforeChecking() {
        assertThat(SpreadsheetSafety.defuse("\t=cmd|'/c calc'!A0")).isEqualTo("'=cmd|'/c calc'!A0");
        assertThat(SpreadsheetSafety.defuse("\r\n=HYPERLINK(\"http://evil\",\"click\")"))
                .startsWith("'=HYPERLINK");
        assertThat(SpreadsheetSafety.defuse("\u0000=1+1")).isEqualTo("'=1+1");
    }

    @Test
    void leavesOrdinaryValuesUntouched() {
        assertThat(SpreadsheetSafety.defuse("Jane Doe")).isEqualTo("Jane Doe");
        assertThat(SpreadsheetSafety.defuse("Mönchengladbach")).isEqualTo("Mönchengladbach");
        assertThat(SpreadsheetSafety.defuse("555-0100")).isEqualTo("555-0100");
    }

    @Test
    void removesControlCharactersFromTheRestOfTheValue() {
        assertThat(SpreadsheetSafety.defuse("Hello\u0000world\u0007")).isEqualTo("Helloworld");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(SpreadsheetSafety.defuse(null)).isEmpty();
        assertThat(SpreadsheetSafety.defuse("")).isEmpty();
        assertThat(SpreadsheetSafety.defuse("\t\t")).isEmpty();
    }

    @Test
    void quotesCsvFieldsThatNeedIt() {
        assertThat(SpreadsheetSafety.toCsvField("Acme, Ltd")).isEqualTo("\"Acme, Ltd\"");
        assertThat(SpreadsheetSafety.toCsvField("He said \"hi\"")).isEqualTo("\"He said \"\"hi\"\"\"");
        assertThat(SpreadsheetSafety.toCsvField("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(SpreadsheetSafety.toCsvField("=1+1")).isEqualTo("'=1+1");
    }
}
