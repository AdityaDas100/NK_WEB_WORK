package com.nkweb.pharma.service.export;

/**
 * Protects the spreadsheet against <b>formula injection</b> (a.k.a. CSV/Excel injection).
 *
 * <p>A cell whose text starts with {@code =}, {@code +}, {@code -} or {@code @} is interpreted as a
 * formula by Excel, LibreOffice and Google Sheets. Since the message field is free text supplied by
 * an anonymous visitor, a payload such as
 * {@code =cmd|'/c calc'!A0} or {@code =HYPERLINK("http://evil","click")} would otherwise execute on
 * the workstation of the person who opens the export – a classic way to escape from a web form into
 * an internal network.
 *
 * <p>Neutralising is done by prefixing an apostrophe, which every spreadsheet application treats as
 * "literal text", and by removing the leading control characters that are used as bypasses
 * ({@code TAB}, {@code CR}, {@code LF} are ignored by some parsers when they look for the formula
 * marker).
 */
public final class SpreadsheetSafety {

    private static final String FORMULA_TRIGGERS = "=+-@";

    private SpreadsheetSafety() {
    }

    /** Makes a value inert when it is written to a spreadsheet cell. */
    public static String defuse(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String withoutLeadingControls = value.replaceAll("^[\\t\\r\\n\\u0000\\u000b\\u000c]+", "");
        if (withoutLeadingControls.isEmpty()) {
            return "";
        }
        char first = withoutLeadingControls.charAt(0);
        if (FORMULA_TRIGGERS.indexOf(first) >= 0) {
            return "'" + withoutLeadingControls;
        }
        // Cell values are plain text: strip the remaining control characters so the workbook stays
        // readable and cannot carry a payload into another tool.
        return withoutLeadingControls.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
    }

    /** Same protection for CSV/TSV values, plus quoting when the value contains a separator. */
    public static String toCsvField(String value) {
        String defused = defuse(value);
        if (defused.contains(";") || defused.contains(",") || defused.contains("\"")
                || defused.contains("\n")) {
            return '"' + defused.replace("\"", "\"\"") + '"';
        }
        return defused;
    }
}
