package com.nkweb.pharma.domain;

/**
 * Lifecycle of a single inquiry with respect to the spreadsheet export.
 *
 * <pre>
 *   PENDING ──claim──▶ PROCESSING ──sink ok────▶ EXPORTED
 *      ▲                    │
 *      └──── retryable ─────┘        (attempts &lt; maxAttempts)
 *                           │
 *                           └────────▶ FAILED   (attempts &gt;= maxAttempts, needs an operator)
 * </pre>
 *
 * <p>The persistence of the inquiry and the export are two independent steps on purpose: the raw
 * submission is durable the moment the client gets its {@code 201}, and the spreadsheet is a
 * derived artefact that may be regenerated from the database at any time.
 */
public enum ExportStatus {

    /** Committed, waiting to be written to the sink. */
    PENDING,

    /** Claimed by a worker; a crashed worker leaves rows here and the sweeper re-queues them. */
    PROCESSING,

    /** Successfully appended to every configured sink. */
    EXPORTED,

    /** Permanently failed after {@code app.export.max-attempts}; requires operator action. */
    FAILED;

    public boolean terminal() {
        return this == EXPORTED || this == FAILED;
    }
}
