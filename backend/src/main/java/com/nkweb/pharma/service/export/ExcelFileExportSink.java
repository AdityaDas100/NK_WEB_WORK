package com.nkweb.pharma.service.export;

import com.nkweb.pharma.config.ExportProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Appends inquiries to an {@code .xlsx} workbook on the local (or mounted) filesystem.
 *
 * <h2>Why this is safe under concurrency</h2>
 * <ul>
 *   <li>All reads and writes happen inside {@link FileWriteLock}, i.e. protected both against
 *       sibling threads and against other processes.</li>
 *   <li>The workbook is written to a temporary file in the <em>same directory</em> and then moved
 *       over the target with {@code ATOMIC_MOVE}. A crash therefore leaves either the old or the new
 *       file, never a half-written workbook.</li>
 *   <li>Every row is keyed by the inquiry reference. Before appending, the references already
 *       present in the sheet are collected and duplicates are skipped, which makes a replay (after
 *       a crash between the write and the database update) a no-op instead of a duplicated lead.</li>
 *   <li>Values pass through {@link SpreadsheetSafety} so a malicious message can never become a
 *       formula that executes on the reviewer's machine.</li>
 * </ul>
 *
 * <p>Rows are appended in batches and the whole workbook is rewritten per batch. A batch is a few
 * hundred rows and an XLSX with tens of thousands of rows is still a few megabytes, which keeps the
 * cost per batch in the low tens of milliseconds. If the file ever grows beyond the point where that
 * is acceptable, switch the sink to a rolling daily workbook (one file per day) or to the Google
 * Sheets/append-only sink – the export pipeline itself does not change.
 */
@Component
public class ExcelFileExportSink implements ExportSink {

    public static final String SINK_NAME = "excel";

    private static final Logger log = LoggerFactory.getLogger(ExcelFileExportSink.class);

    private static final String[] HEADERS = {
            "Reference", "Received (UTC)", "Name", "E-mail", "Phone", "Organisation", "Message", "Consent"};
    private static final int[] COLUMN_WIDTHS = {38, 21, 24, 32, 20, 26, 80, 10};
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(20);

    private final ExportProperties properties;
    private final FileWriteLock writeLock;
    private final Counter writtenRows;
    private final Counter skippedRows;
    private final DataFormatter dataFormatter = new DataFormatter();

    public ExcelFileExportSink(ExportProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.writeLock = new FileWriteLock(LOCK_TIMEOUT);
        this.writtenRows = Counter.builder("pharma.export.rows")
                .tag("sink", SINK_NAME).tag("result", "written").register(meterRegistry);
        this.skippedRows = Counter.builder("pharma.export.rows")
                .tag("sink", SINK_NAME).tag("result", "skipped_duplicate").register(meterRegistry);
    }

    @Override
    public String name() {
        return SINK_NAME;
    }

    @Override
    public void append(List<InquiryExportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Path target = workbookPath();
        createParentDirectory(target);

        int appended = writeLock.runExclusive(target, () -> appendLocked(target, rows));

        int skipped = rows.size() - appended;
        if (skipped > 0) {
            skippedRows.increment(skipped);
        }
        if (appended > 0) {
            writtenRows.increment(appended);
        }
        log.info("export.excel.appended written={} skipped={} file={}",
                appended, skipped, target.getFileName());
    }

    /**
     * Rewrites the workbook from scratch with the supplied rows (disaster recovery: the database is
     * the system of record, the spreadsheet is derived state). Used by
     * {@code app.export.rebuild-on-start=true}.
     */
    public int rebuild(List<InquiryExportRow> rows) {
        Path target = workbookPath();
        createParentDirectory(target);
        int written = writeLock.runExclusive(target, () -> {
            try (XSSFWorkbook workbook = newWorkbook()) {
                Sheet sheet = resolveSheet(workbook);
                RowStyles styles = rowStyles(workbook);
                Set<String> seen = new HashSet<>();
                int rowIndex = 1;
                for (InquiryExportRow row : rows) {
                    if (!seen.add(row.reference())) {
                        continue;
                    }
                    writeRow(sheet, rowIndex++, row, styles);
                }
                refreshAutoFilter(sheet, Math.max(0, rowIndex - 1));
                saveAtomically(workbook, target);
                return rowIndex - 1;
            } catch (IOException e) {
                throw new ExportException("Could not rebuild workbook " + target.getFileName()
                        + ": " + e.getMessage(), e);
            }
        });
        log.info("export.excel.rebuilt rows={} file={}", written, target.getFileName());
        return written;
    }

    /** Absolute location of the workbook; also used by health checks and tests. */
    public Path workbookPath() {
        return Path.of(properties.directory()).toAbsolutePath().normalize()
                .resolve(properties.fileName());
    }

    // ------------------------------------------------------------------
    // internals – always called while holding the write lock
    // ------------------------------------------------------------------

    private int appendLocked(Path target, List<InquiryExportRow> rows) {
        try (XSSFWorkbook workbook = openOrCreateWorkbook(target)) {
            Sheet sheet = resolveSheet(workbook);
            Set<String> existingReferences = readReferences(sheet);
            RowStyles styles = rowStyles(workbook);

            int lastRowIndex = sheet.getLastRowNum();
            int appended = 0;
            for (InquiryExportRow row : rows) {
                if (!existingReferences.add(row.reference())) {
                    continue; // already exported in a previous attempt: never write it twice
                }
                writeRow(sheet, ++lastRowIndex, row, styles);
                appended++;
            }
            if (appended > 0) {
                refreshAutoFilter(sheet, lastRowIndex);
                saveAtomically(workbook, target);
            }
            return appended;
        } catch (IOException e) {
            throw new ExportException("Could not update workbook " + target.getFileName()
                    + ": " + e.getMessage(), e);
        }
    }

    private XSSFWorkbook openOrCreateWorkbook(Path target) throws IOException {
        if (Files.exists(target) && Files.size(target) > 0) {
            try (InputStream input = new BufferedInputStream(Files.newInputStream(target))) {
                XSSFWorkbook workbook = new XSSFWorkbook(input);
                ensureHeader(workbook);
                return workbook;
            } catch (IOException | RuntimeException corrupted) {
                // POI signals a damaged/truncated container with unchecked exceptions as well; the
                // workbook is derived state, so quarantine it and start a new one instead of
                // blocking the pipeline forever.
                quarantine(target, corrupted);
                return newWorkbook();
            }
        }
        return newWorkbook();
    }

    private void quarantine(Path target, Exception cause) {
        Path quarantine = target.resolveSibling(
                target.getFileName() + "." + Instant.now().toEpochMilli() + ".corrupt");
        try {
            Files.move(target, quarantine, StandardCopyOption.REPLACE_EXISTING);
            log.warn("export.excel.quarantined file={} moved_to={} reason={} "
                            + "(rebuild the workbook from the database with app.export.rebuild-on-start=true)",
                    target.getFileName(), quarantine.getFileName(), cause.toString());
        } catch (IOException e) {
            throw new ExportException("Workbook " + target.getFileName()
                    + " is unreadable and could not be quarantined", e);
        }
    }

    private XSSFWorkbook newWorkbook() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.setForceFormulaRecalculation(false);
        Sheet sheet = workbook.createSheet(properties.sheetName());
        writeHeader(workbook, sheet);
        return workbook;
    }

    private void ensureHeader(XSSFWorkbook workbook) {
        Sheet sheet = resolveSheet(workbook);
        Row header = sheet.getRow(0);
        if (header == null || header.getCell(0) == null
                || header.getCell(0).getStringCellValue().isBlank()) {
            writeHeader(workbook, sheet);
        }
    }

    private Sheet resolveSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.getSheet(properties.sheetName());
        if (sheet != null) {
            return sheet;
        }
        // A workbook that was created by hand may have a differently named first sheet: adopt it
        // rather than producing a workbook with two sheets.
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0)
                : workbook.createSheet(properties.sheetName());
    }

    private void writeHeader(XSSFWorkbook workbook, Sheet sheet) {
        CellStyle headerStyle = headerStyle(workbook);
        Row header = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(HEADERS[column]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(column, COLUMN_WIDTHS[column] * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
    }

    private void writeRow(Sheet sheet, int rowIndex, InquiryExportRow inquiry, RowStyles styles) {
        Row row = sheet.createRow(rowIndex);

        row.createCell(0).setCellValue(SpreadsheetSafety.defuse(inquiry.reference()));

        Cell receivedCell = row.createCell(1);
        if (inquiry.receivedAt() != null) {
            receivedCell.setCellValue(Date.from(inquiry.receivedAt()));
            receivedCell.setCellStyle(styles.date());
        }

        row.createCell(2).setCellValue(SpreadsheetSafety.defuse(inquiry.name()));
        row.createCell(3).setCellValue(SpreadsheetSafety.defuse(inquiry.email()));
        row.createCell(4).setCellValue(SpreadsheetSafety.defuse(inquiry.phone()));
        row.createCell(5).setCellValue(SpreadsheetSafety.defuse(inquiry.company()));

        Cell messageCell = row.createCell(6);
        messageCell.setCellValue(SpreadsheetSafety.defuse(inquiry.message()));
        messageCell.setCellStyle(styles.message());

        row.createCell(7).setCellValue(inquiry.consent() ? "YES" : "NO");
    }

    /** Collects the references already present so that replays stay idempotent. */
    private Set<String> readReferences(Sheet sheet) {
        Set<String> references = new HashSet<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(0);
            if (cell == null) {
                continue;
            }
            String value = dataFormatter.formatCellValue(cell).strip();
            if (!value.isEmpty()) {
                references.add(value);
            }
        }
        return references;
    }

    private void refreshAutoFilter(Sheet sheet, int lastRowIndex) {
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, lastRowIndex), 0, HEADERS.length - 1));
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private RowStyles rowStyles(XSSFWorkbook workbook) {
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

        // Keeps long messages readable without turning the file into a wall of text.
        CellStyle messageStyle = workbook.createCellStyle();
        messageStyle.setWrapText(true);
        messageStyle.setVerticalAlignment(VerticalAlignment.TOP);

        return new RowStyles(dateStyle, messageStyle);
    }

    /** Cell styles reused for every data row of a workbook (styles are workbook scoped). */
    private record RowStyles(CellStyle date, CellStyle message) {
    }

    private void saveAtomically(XSSFWorkbook workbook, Path target) throws IOException {
        Path directory = target.getParent() == null ? Path.of(".") : target.getParent();
        Path temporary = Files.createTempFile(directory, ".inquiries-", ".xlsx.tmp");
        try {
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                workbook.write(output);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // Network shares and some Windows configurations do not support atomic moves; the
                // write lock still guarantees that no other writer interleaves with the copy.
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void createParentDirectory(Path target) {
        Path directory = target.getParent();
        if (directory == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new ExportException("Could not create export directory " + directory, e);
        }
    }
}
