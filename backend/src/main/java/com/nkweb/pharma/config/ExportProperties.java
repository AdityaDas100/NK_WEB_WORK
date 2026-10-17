package com.nkweb.pharma.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Configuration of the asynchronous spreadsheet export pipeline.
 *
 * @param enabled        when false inquiries are still persisted but never exported.
 * @param sinks          one or more of {@code excel}, {@code google-sheets}. Every configured
 *                       sink receives every batch, which allows running the file export and a
 *                       cloud copy in parallel.
 * @param directory      directory that holds the workbook (created on demand).
 * @param fileName       workbook file name.
 * @param sheetName      worksheet that receives the rows.
 * @param batchSize      maximum number of rows handed to a sink in a single batch.
 * @param maxAttempts    number of export attempts before a row is parked as {@code FAILED}.
 * @param sweepInterval  how often the recovery sweeper looks for unexported rows.
 * @param initialDelay   delay before the first sweep after startup.
 * @param googleSheets   settings for the Google Sheets (Apps Script) sink.
 */
@ConfigurationProperties(prefix = "app.export")
public record ExportProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("excel") List<String> sinks,
        @DefaultValue("./data/exports") String directory,
        @DefaultValue("inquiries.xlsx") String fileName,
        @DefaultValue("Inquiries") String sheetName,
        @DefaultValue("250") int batchSize,
        @DefaultValue("6") int maxAttempts,
        @DefaultValue("15s") Duration sweepInterval,
        @DefaultValue("20s") Duration initialDelay,
        @DefaultValue GoogleSheets googleSheets) {

    public ExportProperties {
        sinks = sinks == null || sinks.isEmpty() ? List.of("excel") : List.copyOf(sinks);
        if (batchSize < 1 || batchSize > 5000) {
            throw new IllegalArgumentException("app.export.batch-size must be between 1 and 5000");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.export.max-attempts must be >= 1");
        }
    }

    /**
     * Google Sheets is written through an Apps Script web app: the spreadsheet owner deploys a
     * small script that appends rows on {@code POST}, which avoids shipping spreadsheet
     * credentials into the application and works with any Google account/quotas.
     *
     * @param webhookUrl  Apps Script deployment URL. Blank disables the sink.
     * @param bearerToken shared secret sent as {@code Authorization: Bearer ...} (validated by
     *                    the script) and as a second factor against spam.
     * @param timeout     per-request timeout.
     */
    public record GoogleSheets(
            @DefaultValue("") String webhookUrl,
            @DefaultValue("") String bearerToken,
            @DefaultValue("10s") Duration timeout) {

        public boolean configured() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }
    }
}
