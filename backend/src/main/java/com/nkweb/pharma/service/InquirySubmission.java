package com.nkweb.pharma.service;

import com.nkweb.pharma.domain.Inquiry;

import java.time.Instant;
import java.util.UUID;

/**
 * Outcome of a submission.
 *
 * @param reference  public reference of the stored inquiry.
 * @param receivedAt acceptance timestamp (UTC).
 * @param duplicate  true when an idempotent retry hit an already stored inquiry.
 */
public record InquirySubmission(UUID reference, Instant receivedAt, boolean duplicate) {

    static InquirySubmission created(Inquiry inquiry) {
        return new InquirySubmission(inquiry.getPublicId(), inquiry.getCreatedAt(), false);
    }

    static InquirySubmission replayed(Inquiry inquiry) {
        return new InquirySubmission(inquiry.getPublicId(), inquiry.getCreatedAt(), true);
    }
}
