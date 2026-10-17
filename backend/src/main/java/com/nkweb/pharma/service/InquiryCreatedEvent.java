package com.nkweb.pharma.service;

import java.util.UUID;

/**
 * Published <em>inside</em> the intake transaction and consumed after it commits.
 *
 * <p>This is the "transactional outbox" trick in its simplest form: because the listener runs only
 * for committed data (or for {@code AFTER_COMPLETION} in the sweeper's case), the export can never
 * observe – let alone export – a row that was rolled back.
 *
 * @param reference public reference ({@code public_id}) of the accepted inquiry.
 */
public record InquiryCreatedEvent(UUID reference) {
}
