package com.nkweb.pharma.service.export;

import com.nkweb.pharma.repository.InquiryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Operator tool: rebuilds the workbook from the database.
 *
 * <p>The database is the system of record and the spreadsheet is derived state, so a workbook that
 * was lost, quarantined (corrupt) or manually edited can be regenerated without any data loss:
 *
 * <pre>{@code
 * java -jar pharma-inquiry-service.jar --app.export.rebuild-on-start=true
 * }</pre>
 *
 * <p>The rebuild happens before the scheduled sweeper's first pass (its initial delay is 20s by
 * default) and uses the same write lock, so it can never interleave with an append.
 */
@Component
@ConditionalOnProperty(prefix = "app.export", name = "rebuild-on-start", havingValue = "true")
public class WorkbookRebuildRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkbookRebuildRunner.class);

    private final InquiryRepository repository;
    private final ExcelFileExportSink excelSink;

    public WorkbookRebuildRunner(InquiryRepository repository, ExcelFileExportSink excelSink) {
        this.repository = repository;
        this.excelSink = excelSink;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        log.info("export.rebuild.started file={}", excelSink.workbookPath().getFileName());
        List<InquiryExportRow> rows = repository.findAllByOrderByIdAsc()
                .stream()
                .map(InquiryExportRow::from)
                .toList();
        int written = excelSink.rebuild(rows);
        log.info("export.rebuild.completed rows={}", written);
    }
}
