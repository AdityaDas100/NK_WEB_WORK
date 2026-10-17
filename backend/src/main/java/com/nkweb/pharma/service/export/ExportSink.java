package com.nkweb.pharma.service.export;

import java.util.List;

/**
 * Destination for exported inquiries.
 *
 * <p>Implementations must be <b>idempotent per reference</b>: the same inquiry may be handed to a
 * sink more than once (the worker retries after an outage, a crash can happen between the write and
 * the database update). Sinks therefore either de-duplicate on the reference column or perform an
 * upsert – appending blindly is not acceptable.
 *
 * <p>Implementations must also be safe when two threads of the same JVM and two JVMs call
 * {@link #append} at the same time (see {@link FileWriteLock} for the file based sink).
 */
public interface ExportSink {

    /** Stable identifier used in configuration, metrics and logs, e.g. {@code excel}. */
    String name();

    /**
     * Writes the batch.
     *
     * @throws ExportException when the batch could not be delivered; the caller re-queues the rows.
     */
    void append(List<InquiryExportRow> rows);
}
