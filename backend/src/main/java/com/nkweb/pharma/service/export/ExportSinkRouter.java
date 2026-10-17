package com.nkweb.pharma.service.export;

import com.nkweb.pharma.config.ExportProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fans a batch out to every configured sink.
 *
 * <p>Failure semantics: if one sink fails, the batch is reported as failed even if another sink
 * succeeded. The rows are re-queued and retried, and because every sink is idempotent per reference
 * the sink that already succeeded simply skips those rows on the next attempt. This gives
 * "at least once delivery, effectively once applied" without a distributed transaction.
 *
 * <p>This bean is {@link Primary @Primary}: it is the composite that the rest of the application
 * (the export worker) injects when it asks for "the" {@link ExportSink}. The concrete sinks are
 * only wired in here, by their own types, which is also why this class must <em>not</em> inject a
 * {@code List<ExportSink>} - that would make it a member of its own delegate list.
 */
@Component
@Primary
public class ExportSinkRouter implements ExportSink {

    private final List<ExportSink> activeSinks;

    public ExportSinkRouter(ExportProperties properties,
                            ExcelFileExportSink excelSink,
                            ObjectProvider<GoogleSheetsExportSink> googleSheetsSink) {
        Map<String, ExportSink> registry = new LinkedHashMap<>();
        registry.put(ExcelFileExportSink.SINK_NAME, excelSink);
        GoogleSheetsExportSink googleSheets = googleSheetsSink.getIfAvailable();
        if (googleSheets != null) {
            registry.put(GoogleSheetsExportSink.SINK_NAME, googleSheets);
        }

        List<ExportSink> resolved = new ArrayList<>();
        for (String configured : properties.sinks()) {
            String name = configured.strip().toLowerCase(Locale.ROOT);
            ExportSink sink = registry.get(name);
            if (sink == null) {
                throw new IllegalStateException("Unknown export sink '" + configured
                        + "' in app.export.sinks. Available: " + registry.keySet());
            }
            if (!resolved.contains(sink)) {
                resolved.add(sink);
            }
        }
        this.activeSinks = List.copyOf(resolved);
    }

    @Override
    public String name() {
        return "router";
    }

    @Override
    public void append(List<InquiryExportRow> rows) {
        List<String> failures = new ArrayList<>();
        for (ExportSink sink : activeSinks) {
            try {
                sink.append(rows);
            } catch (RuntimeException failure) {
                failures.add(sink.name() + ": " + failure.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new ExportException("export sink failure(s) -> " + String.join(" | ", failures));
        }
    }

    public List<String> activeSinkNames() {
        return activeSinks.stream().map(ExportSink::name).toList();
    }
}
