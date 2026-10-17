package com.nkweb.pharma.service.export;

import com.nkweb.pharma.config.ExportProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors inquiries into a Google Sheet through an Apps Script web app.
 *
 * <p>Why a webhook instead of the Sheets API with a service account:
 * <ul>
 *   <li>no long-lived credential or JSON key file has to be baked into the container (a stolen
 *       service-account key is a much bigger problem than a webhook URL);</li>
 *   <li>it works with personal Google accounts and with Workspace accounts that forbid service
 *       accounts, and it keeps the spreadsheet permission model with the sheet owner;</li>
 *   <li>the script can de-duplicate on the reference column, which is exactly the idempotency
 *       contract {@link ExportSink} requires (see {@code docs/EXPORT.md} for the script).</li>
 * </ul>
 *
 * <p>Only registered when {@code app.export.google-sheets.webhook-url} is configured.
 */
@Component
@ConditionalOnProperty(prefix = "app.export.google-sheets", name = "webhook-url")
public class GoogleSheetsExportSink implements ExportSink {

    public static final String SINK_NAME = "google-sheets";

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsExportSink.class);

    private final RestClient client;
    private final String bearerToken;
    private final Counter writtenRows;
    private final Counter failedBatches;

    public GoogleSheetsExportSink(ExportProperties properties, MeterRegistry meterRegistry) {
        ExportProperties.GoogleSheets googleSheets = properties.googleSheets();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Math.max(1, googleSheets.timeout().toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        this.client = RestClient.builder()
                .baseUrl(googleSheets.webhookUrl())
                .requestFactory(requestFactory)
                .build();
        this.bearerToken = googleSheets.bearerToken();
        this.writtenRows = Counter.builder("pharma.export.rows")
                .tag("sink", SINK_NAME).tag("result", "written").register(meterRegistry);
        this.failedBatches = Counter.builder("pharma.export.batches")
                .tag("sink", SINK_NAME).tag("result", "failed").register(meterRegistry);
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rows", rows.stream().map(InquiryExportRow::toMap).toList());
        if (!bearerToken.isBlank()) {
            // A Google Apps Script web app cannot read request headers at all, so the shared secret
            // is sent in the body as well; the reference implementation validates this field
            // (docs/EXPORT.md). Sending it twice costs nothing and keeps the sink usable behind a
            // proxy that does check the Authorization header.
            payload.put("token", bearerToken);
        }

        try {
            client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!bearerToken.isBlank()) {
                            headers.setBearerAuth(bearerToken);
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            writtenRows.increment(rows.size());
            log.info("export.google_sheets.appended rows={}", rows.size());
        } catch (RestClientException e) {
            failedBatches.increment();
            // The exception message may contain the response body; only the status/class is logged
            // so that no inquiry content ends up in the log stream.
            throw new ExportException("Google Sheets webhook rejected the batch ("
                    + e.getClass().getSimpleName() + ")", e);
        }
    }
}
