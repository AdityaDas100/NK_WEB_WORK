package com.nkweb.pharma.service.export;

import com.nkweb.pharma.config.AsyncConfig;
import com.nkweb.pharma.config.ExportProperties;
import com.nkweb.pharma.service.InquiryCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Drains the inquiry outbox into the configured sinks.
 *
 * <h2>Two triggers, one drain</h2>
 * <ol>
 *   <li>{@link #onInquiryCreated} – an inquiry was just committed. Running the listener after the
 *       commit (and on a background executor) keeps the HTTP latency independent of the spreadsheet
 *       I/O.</li>
 *   <li>{@link #sweep} – a timer that picks up whatever the first trigger missed: a crashed process,
 *       a sink that was temporarily unavailable, a row that exhausted fewer attempts than allowed.
 *       This is what turns "best effort async write" into an at-least-once pipeline.</li>
 * </ol>
 *
 * <h2>Concurrency</h2>
 * <p>{@link #drainLock} serialises drains inside one JVM (a second caller returns immediately: the
 * running drain will pick up its rows anyway). Across JVMs, disjointness is guaranteed by
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} in {@link ExportClaimService}, and the file sink adds a
 * cross-process file lock on top. Dropping an event therefore cannot lose data – the sweeper is the
 * backstop.
 *
 * <h2>Failure handling</h2>
 * <p>Any failure (including a runtime exception from a sink implementation) marks the batch as
 * failed, i.e. back to {@code PENDING} for a retry, and after {@code app.export.max-attempts} as
 * {@code FAILED} for an operator. Rows are never silently dropped.
 */
@Component
public class InquiryExportWorker {

    private static final Logger log = LoggerFactory.getLogger(InquiryExportWorker.class);

    /** How long a claimed row may stay untouched before it is considered abandoned. */
    private static final Duration ABANDONED_AFTER = Duration.ofMinutes(5);
    private static final int ERROR_MESSAGE_LIMIT = 300;

    private final ExportClaimService claimService;
    private final ExportSink sink;
    private final ExportProperties properties;
    private final Counter exportedRows;
    private final Counter failedBatches;
    private final Counter recoveredRows;
    private final ReentrantLock drainLock = new ReentrantLock(true);

    public InquiryExportWorker(ExportClaimService claimService,
                               ExportSink sink,
                               ExportProperties properties,
                               MeterRegistry meterRegistry) {
        this.claimService = claimService;
        this.sink = sink;
        this.properties = properties;
        this.exportedRows = meterRegistry.counter("pharma.export.rows",
                "sink", "pipeline", "result", "exported");
        this.failedBatches = meterRegistry.counter("pharma.export.batches", "result", "failed");
        this.recoveredRows = meterRegistry.counter("pharma.export.rows", "result", "recovered");

        Gauge.builder("pharma.export.backlog", claimService,
                        service -> safeCount(service::pendingCount))
                .description("Inquiries waiting to be exported")
                .register(meterRegistry);
        Gauge.builder("pharma.export.parked", claimService,
                        service -> safeCount(service::failedCount))
                .description("Inquiries whose export failed permanently and needs an operator")
                .register(meterRegistry);

        log.info("export.worker.configured sinks={} enabled={} batch_size={} sweep_interval={}",
                sink.name(), properties.enabled(), properties.batchSize(), properties.sweepInterval());
    }

    /** Fast path: an inquiry was committed, so try to export it right away. */
    @Async(AsyncConfig.EXPORT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInquiryCreated(InquiryCreatedEvent event) {
        if (!properties.enabled()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("export.trigger reference={}", event.reference());
        }
        drainPending();
    }

    /** Recovery path: periodically re-queue abandoned rows and drain whatever is pending. */
    @Scheduled(fixedDelayString = "${app.export.sweep-interval:15s}",
            initialDelayString = "${app.export.initial-delay:20s}")
    public void sweep() {
        if (!properties.enabled()) {
            return;
        }
        try {
            int recovered = claimService.requeueStaleProcessing(ABANDONED_AFTER);
            if (recovered > 0) {
                recoveredRows.increment(recovered);
                log.warn("export.recovered_abandoned rows={}", recovered);
            }
        } catch (RuntimeException e) {
            log.error("export.recovery_failed", e);
        }
        drainPending();
    }

    /**
     * Exports every pending inquiry in batches until the queue is empty.
     *
     * @return the number of rows handed to the sinks during this call.
     */
    public int drainPending() {
        if (!properties.enabled()) {
            return 0;
        }
        if (!drainLock.tryLock()) {
            // Another drain is running in this JVM; it will observe the rows we were called for.
            log.debug("export.drain.skipped reason=already-running");
            return 0;
        }
        try {
            int totalExported = 0;
            while (true) {
                List<InquiryExportRow> batch = claimService.claimBatch(
                        properties.batchSize(), properties.maxAttempts());
                if (batch.isEmpty()) {
                    break;
                }
                if (!writeBatch(batch)) {
                    break;
                }
                totalExported += batch.size();
                if (batch.size() < properties.batchSize()) {
                    break;
                }
            }
            if (totalExported > 0) {
                log.info("export.drain.completed rows={} sinks={}", totalExported, sink.name());
            }
            return totalExported;
        } finally {
            drainLock.unlock();
        }
    }

    /** @return true when the batch was delivered, false when it has to be retried later. */
    private boolean writeBatch(List<InquiryExportRow> batch) {
        List<UUID> references = batch.stream().map(InquiryExportRow::referenceUuid).toList();
        try {
            sink.append(batch);
            int updated = claimService.markExported(references);
            exportedRows.increment(batch.size());
            if (updated < batch.size()) {
                log.warn("export.marked.exported.mismatch batch={} updated={}", batch.size(), updated);
            }
            return true;
        } catch (RuntimeException failure) {
            // Catch everything: a sink is third-party code and an unexpected exception must not
            // leave the rows stuck in PROCESSING.
            failedBatches.increment();
            String reason = describe(failure);
            log.error("export.batch.failed rows={} sink={} reason={}",
                    batch.size(), sink.name(), reason, failure);
            try {
                claimService.markFailed(references, reason, properties.maxAttempts());
            } catch (RuntimeException databaseFailure) {
                // The rows stay PROCESSING and the sweeper will recover them.
                log.error("export.mark_failed.failed rows={} (rows will be recovered by the sweeper)",
                        batch.size(), databaseFailure);
            }
            return false;
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getClass().getSimpleName();
        if (failure.getMessage() != null) {
            message = message + ": " + failure.getMessage();
        }
        String sanitised = message.replaceAll("[\\p{Cntrl}]", " ");
        return sanitised.length() > ERROR_MESSAGE_LIMIT
                ? sanitised.substring(0, ERROR_MESSAGE_LIMIT)
                : sanitised;
    }

    /** A gauge must never throw: a database outage would otherwise break the metrics scrape. */
    private static long safeCount(LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (RuntimeException e) {
            log.debug("export.gauge.unavailable reason={}", e.getClass().getSimpleName());
            return -1L;
        }
    }
}
