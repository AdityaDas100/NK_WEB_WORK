package com.nkweb.pharma.service.export;

import com.nkweb.pharma.config.ExportProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural tests of the file sink against a real {@code .xlsx} on a real (temporary) filesystem.
 *
 * <p>These are deliberately not mocked: the whole point of the sink is that it stays correct when
 * several threads, a replay and a damaged file meet the same workbook.
 */
class ExcelFileExportSinkTest {

    private static final int COLUMN_REFERENCE = 0;
    private static final int COLUMN_NAME = 2;
    private static final int COLUMN_MESSAGE = 6;

    // ------------------------------------------------------------------
    // happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("creates the workbook, its header and the row")
    void createsWorkbookWithHeaderAndRow(@TempDir Path directory) throws IOException {
        ExcelFileExportSink sink = newSink(directory);

        sink.append(List.of(row("11111111-1111-1111-1111-111111111111")));

        Path workbook = sink.workbookPath();
        assertThat(workbook).exists();
        assertThat(dataRowCount(workbook)).isEqualTo(1);
        assertThat(cell(workbook, 0, COLUMN_REFERENCE))
                .isEqualTo("Reference");
        assertThat(cell(workbook, 1, COLUMN_REFERENCE))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(cell(workbook, 1, COLUMN_NAME)).isEqualTo("Jane Doe");
    }

    @Test
    void ignoresEmptyBatches(@TempDir Path directory) {
        ExcelFileExportSink sink = newSink(directory);

        sink.append(List.of());
        sink.append(null);

        assertThat(sink.workbookPath()).doesNotExist();
    }

    // ------------------------------------------------------------------
    // idempotency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a replayed batch is a no-op, not a duplicated lead")
    void replayingTheSameBatchDoesNotDuplicateRows(@TempDir Path directory) throws IOException {
        ExcelFileExportSink sink = newSink(directory);
        List<InquiryExportRow> batch = List.of(
                row(UUID.randomUUID().toString()),
                row(UUID.randomUUID().toString()),
                row(UUID.randomUUID().toString()));

        sink.append(batch);
        sink.append(batch);

        assertThat(dataRowCount(sink.workbookPath())).isEqualTo(3);
    }

    @Test
    void dropsDuplicateReferencesInsideOneBatch(@TempDir Path directory) throws IOException {
        ExcelFileExportSink sink = newSink(directory);
        String reference = UUID.randomUUID().toString();

        sink.append(List.of(row(reference), row(reference), row(UUID.randomUUID().toString())));

        assertThat(dataRowCount(sink.workbookPath())).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // concurrency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("eight threads appending 25 rows each produce exactly 200 rows")
    void concurrentAppendsDoNotLoseRows(@TempDir Path directory) throws Exception {
        ExcelFileExportSink sink = newSink(directory);
        int threads = 8;
        int rowsPerThread = 25;

        List<String> references = new ArrayList<>(threads * rowsPerThread);
        for (int index = 0; index < threads * rowsPerThread; index++) {
            references.add(UUID.randomUUID().toString());
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLine = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                int offset = thread * rowsPerThread;
                tasks.add(pool.submit(() -> {
                    startLine.await();
                    for (int index = 0; index < rowsPerThread; index++) {
                        sink.append(List.of(row(references.get(offset + index))));
                    }
                    return null;
                }));
            }
            startLine.countDown();
            for (Future<?> task : tasks) {
                task.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Path workbook = sink.workbookPath();
        assertThat(dataRowCount(workbook)).isEqualTo(threads * rowsPerThread);
        assertThat(references(workbook)).hasSize(threads * rowsPerThread);
    }

    @Test
    void keepsASiblingLockFileNextToTheWorkbook(@TempDir Path directory) throws IOException {
        ExcelFileExportSink sink = newSink(directory);

        sink.append(List.of(row(UUID.randomUUID().toString())));

        assertThat(FileWriteLock.lockFileFor(sink.workbookPath())).exists();
        assertThat(sink.workbookPath().getFileName().toString()).isEqualTo("inquiries.xlsx");
    }

    // ------------------------------------------------------------------
    // spreadsheet safety
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a message that looks like a formula is stored as literal text")
    void defusesFormulaInjection(@TempDir Path directory) throws IOException {
        ExcelFileExportSink sink = newSink(directory);
        InquiryExportRow hostile = new InquiryExportRow(
                UUID.randomUUID().toString(),
                Instant.parse("2026-01-02T03:04:05Z"),
                "Jane Doe",
                "jane@example.com",
                "+1 555 010 9999",
                "Acme Pharma Ltd",
                "=cmd|'/c calc'!A0",
                true);

        sink.append(List.of(hostile));

        assertThat(cell(sink.workbookPath(), 1, COLUMN_MESSAGE)).isEqualTo("'=cmd|'/c calc'!A0");
    }

    // ------------------------------------------------------------------
    // recovery
    // ------------------------------------------------------------------

    @Test
    @DisplayName("rebuild replaces the workbook from the database rows")
    void rebuildRewritesTheWorkbook(@TempDir Path directory) throws IOException {
        ExcelFileExportSink sink = newSink(directory);
        sink.append(List.of(row(UUID.randomUUID().toString()), row(UUID.randomUUID().toString())));

        int written = sink.rebuild(List.of(
                row(UUID.randomUUID().toString()),
                row(UUID.randomUUID().toString()),
                row(UUID.randomUUID().toString())));

        assertThat(written).isEqualTo(3);
        assertThat(dataRowCount(sink.workbookPath())).isEqualTo(3);
    }

    @Test
    void rebuildDeduplicatesReferences(@TempDir Path directory) {
        ExcelFileExportSink sink = newSink(directory);
        String reference = UUID.randomUUID().toString();

        int written = sink.rebuild(List.of(row(reference), row(reference)));

        assertThat(written).isEqualTo(1);
    }

    @Test
    @DisplayName("a damaged workbook is quarantined instead of blocking the pipeline")
    void quarantinesADamagedWorkbook(@TempDir Path directory) throws IOException {
        ExcelFileExportSink sink = newSink(directory);
        Path workbook = sink.workbookPath();
        Files.createDirectories(workbook.getParent());
        Files.writeString(workbook, "this is definitely not a spreadsheet");

        sink.append(List.of(row(UUID.randomUUID().toString())));

        assertThat(dataRowCount(workbook)).isEqualTo(1);
        try (Stream<Path> files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".corrupt")))
                    .hasSize(1);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ExcelFileExportSink newSink(Path directory) {
        ExportProperties properties = new ExportProperties(
                true,
                List.of(ExcelFileExportSink.SINK_NAME),
                directory.toString(),
                "inquiries.xlsx",
                "Inquiries",
                250,
                6,
                Duration.ofSeconds(15),
                Duration.ofSeconds(20),
                new ExportProperties.GoogleSheets("", "", Duration.ofSeconds(10)));
        return new ExcelFileExportSink(properties, new SimpleMeterRegistry());
    }

    private static InquiryExportRow row(String reference) {
        return new InquiryExportRow(
                reference,
                Instant.parse("2026-01-02T03:04:05Z"),
                "Jane Doe",
                "jane.doe@example.com",
                "+1 555 010 9999",
                "Acme Pharma Ltd",
                "We would like pricing for Cardolix 20.",
                true);
    }

    /** Row 0 is the header, so the last row index equals the number of data rows. */
    private static int dataRowCount(Path workbook) throws IOException {
        try (XSSFWorkbook opened = open(workbook)) {
            Sheet sheet = opened.getSheet("Inquiries");
            return sheet == null ? 0 : sheet.getLastRowNum();
        }
    }

    private static String cell(Path workbook, int rowIndex, int columnIndex) throws IOException {
        try (XSSFWorkbook opened = open(workbook)) {
            return new DataFormatter()
                    .formatCellValue(opened.getSheet("Inquiries").getRow(rowIndex).getCell(columnIndex));
        }
    }

    private static Set<String> references(Path workbook) throws IOException {
        try (XSSFWorkbook opened = open(workbook)) {
            DataFormatter formatter = new DataFormatter();
            Sheet sheet = opened.getSheet("Inquiries");
            Set<String> collected = new HashSet<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                collected.add(formatter.formatCellValue(sheet.getRow(index).getCell(COLUMN_REFERENCE)));
            }
            return collected;
        }
    }

    private static XSSFWorkbook open(Path workbook) throws IOException {
        try (InputStream input = Files.newInputStream(workbook)) {
            return new XSSFWorkbook(input);
        }
    }
}
