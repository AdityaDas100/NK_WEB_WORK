package com.nkweb.pharma.repository;

import com.nkweb.pharma.domain.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    /**
     * Idempotency lookup: identifies the inquiry a retried submission refers to.
     */
    Optional<Inquiry> findByClientRequestId(String clientRequestId);

    List<Inquiry> findAllByPublicIdIn(Collection<UUID> publicIds);

    /**
     * Claims the next batch of rows for export.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} is what makes the pipeline safe to run on several
     * instances: two workers asking for a batch at the same time receive <em>disjoint</em> sets of
     * rows instead of blocking each other or exporting the same row twice. Rows are ordered by
     * {@code id} so that the spreadsheet keeps a stable, chronological order.
     *
     * <p>Must be called inside a transaction (enforced by the calling service).
     */
    @Query(value = """
            SELECT *
              FROM inquiries
             WHERE export_status = 'PENDING'
               AND export_attempts < :maxAttempts
             ORDER BY id
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Inquiry> claimPendingBatch(@Param("batchSize") int batchSize,
                                    @Param("maxAttempts") int maxAttempts);

    long countByExportStatus(com.nkweb.pharma.domain.ExportStatus status);

    /** Every row, oldest first: used by the operator-triggered workbook rebuild. */
    List<Inquiry> findAllByOrderByIdAsc();

    /**
     * Re-queues rows that a worker claimed but never finished, for example because the process was
     * killed between the claim and the update. Without this a crash would leave rows stuck in
     * {@code PROCESSING} forever, invisible to the claim query.
     *
     * <p>Bulk JPQL on purpose: this can affect many rows while no persistence context is open, and
     * it is the only statement that transitions {@code PROCESSING} backwards.
     *
     * @return number of recovered rows.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inquiry i
               SET i.exportStatus = :pending
             WHERE i.exportStatus = :processing
               AND i.updatedAt < :cutoff
            """)
    int requeueStaleProcessing(@Param("pending") com.nkweb.pharma.domain.ExportStatus pending,
                               @Param("processing") com.nkweb.pharma.domain.ExportStatus processing,
                               @Param("cutoff") Instant cutoff);
}
