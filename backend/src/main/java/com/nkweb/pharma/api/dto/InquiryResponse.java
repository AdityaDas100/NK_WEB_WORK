package com.nkweb.pharma.api.dto;

import java.time.Instant;

/**
 * Response returned for an accepted (or replayed) inquiry. Contains no personal data – only the
 * reference the visitor can quote, so nothing sensitive is reflected back to the browser.
 *
 * @param reference  opaque inquiry reference (UUID).
 * @param status     {@code RECEIVED} for a new submission, {@code DUPLICATE} when an idempotent
 *                   retry of an already accepted submission was detected.
 * @param receivedAt server-side timestamp of the original acceptance (UTC).
 */
public record InquiryResponse(
        String reference,
        String status,
        Instant receivedAt) {

    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_DUPLICATE = "DUPLICATE";
}
