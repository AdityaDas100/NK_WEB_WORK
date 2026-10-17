package com.nkweb.pharma.service.export;

import com.nkweb.pharma.domain.ExportStatus;
import com.nkweb.pharma.domain.Inquiry;
import com.nkweb.pharma.repository.InquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * All database state transitions of the export pipeline, in one place and always transactional.
 *
 * <p>Keeping the queries here (instead of inside the worker) means the worker never opens a
 * transaction around slow I/O: it claims a batch, <em>commits</em>, writes to the sink without any
 * database connection in hand, and then records the outcome. A sink that hangs therefore cannot
 * pin a connection from the HikariCP pool – the single most common way an "async export" takes a
 * production database down with it.
 */
@Service
public class ExportClaimService {

    private final InquiryRepository repository;

    public ExportClaimService(InquiryRepository repository) {
        this.repository = repository;
    }

    /**
     * Atomically claims up to {@code batchSize} pending rows and marks them {@code PROCESSING}.
     *
     * @return detached snapshots of the claimed rows, ready to be handed to a sink.
     */
    @Transactional
    public List<InquiryExportRow> claimBatch(int batchSize, int maxAttempts) {
        List<Inquiry> claimed = repository.claimPendingBatch(batchSize, maxAttempts);
        if (claimed.isEmpty()) {
            return List.of();
        }
        claimed.forEach(Inquiry::markProcessing);
        repository.flush();
        return claimed.stream().map(InquiryExportRow::from).toList();
    }

    /** Marks the rows as exported. Idempotent: rows already exported are simply ignored. */
    @Transactional
    public int markExported(Collection<UUID> references) {
        List<Inquiry> rows = repository.findAllByPublicIdIn(references);
        Instant now = Instant.now();
        int updated = 0;
        for (Inquiry row : rows) {
            if (row.getExportStatus() == ExportStatus.EXPORTED) {
                continue;
            }
            row.markExported(now);
            updated++;
        }
        repository.flush();
        return updated;
    }

    /**
     * Records a failure: rows go back to {@code PENDING} (and are retried by the sweeper) until
     * {@code maxAttempts} is exhausted, after which they are parked as {@code FAILED}.
     */
    @Transactional
    public int markFailed(Collection<UUID> references, String error, int maxAttempts) {
        List<Inquiry> rows = repository.findAllByPublicIdIn(references);
        for (Inquiry row : rows) {
            row.markFailed(error, maxAttempts);
        }
        repository.flush();
        return rows.size();
    }

    /**
     * Crash recovery: rows a dead worker left in {@code PROCESSING} go back to the queue.
     *
     * @param olderThan how long a row must have been untouched to be considered abandoned. Must be
     *                  comfortably larger than the longest expected export duration.
     * @return number of recovered rows.
     */
    @Transactional
    public int requeueStaleProcessing(Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        return repository.requeueStaleProcessing(
                ExportStatus.PENDING, ExportStatus.PROCESSING, cutoff);
    }

    /** Backlog size, used for the {@code pharma.export.backlog} gauge. */
    @Transactional(readOnly = true)
    public long pendingCount() {
        return repository.countByExportStatus(ExportStatus.PENDING);
    }

    /** Rows parked as {@code FAILED}; a non-zero value deserves an alert. */
    @Transactional(readOnly = true)
    public long failedCount() {
        return repository.countByExportStatus(ExportStatus.FAILED);
    }
}
