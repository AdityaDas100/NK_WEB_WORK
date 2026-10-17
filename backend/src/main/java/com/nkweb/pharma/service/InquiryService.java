package com.nkweb.pharma.service;

import com.nkweb.pharma.api.dto.InquiryRequest;
import com.nkweb.pharma.domain.Inquiry;
import com.nkweb.pharma.repository.InquiryRepository;
import com.nkweb.pharma.security.PiiMasker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * Intake use case.
 *
 * <p>Guarantees:
 * <ul>
 *   <li><b>Durability</b> – the inquiry is committed before the caller returns; the HTTP 201 is
 *       only produced for data that a subsequent request is guaranteed to see.</li>
 *   <li><b>Idempotency</b> – the {@code client_request_id} unique index combined with a
 *       pre-check makes a retried submission (double click, flaky network, proxy retry) return the
 *       original reference instead of inserting a duplicate.</li>
 *   <li><b>No PII in logs</b> – only masked values and HMAC fingerprints are logged.</li>
 *   <li><b>Hand-off</b> – the export event is published inside the transaction, so the asynchronous
 *       worker is triggered exactly once per committed row and again by the sweeper if it fails.</li>
 * </ul>
 *
 * <p>The transactional work is executed through {@link TransactionTemplate} rather than through a
 * {@code @Transactional} method: the idempotency recovery path must run <em>after</em> the failed
 * transaction has been rolled back, which is impossible from inside the same transactional method.
 */
@Service
public class InquiryService {

    private static final Logger log = LoggerFactory.getLogger(InquiryService.class);

    private final InquiryRepository repository;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final Counter accepted;
    private final Counter duplicates;
    private final Timer intakeTimer;

    public InquiryService(InquiryRepository repository,
                          ApplicationEventPublisher events,
                          TransactionTemplate transactionTemplate,
                          MeterRegistry meterRegistry) {
        this.repository = repository;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.accepted = meterRegistry.counter("pharma.inquiries.accepted");
        this.duplicates = meterRegistry.counter("pharma.inquiries.duplicate");
        this.intakeTimer = meterRegistry.timer("pharma.inquiries.intake");
    }

    /**
     * Stores a submission and schedules the export.
     *
     * @throws DataIntegrityViolationException when the unique constraint is violated for a reason
     *                                         other than a concurrent duplicate.
     */
    public InquirySubmission submit(InquiryRequest request, ClientMetadata metadata) {
        return intakeTimer.record(() -> doSubmit(request, metadata));
    }

    private InquirySubmission doSubmit(InquiryRequest request, ClientMetadata metadata) {

        Optional<Inquiry> alreadyStored = repository.findByClientRequestId(metadata.clientRequestId());
        if (alreadyStored.isPresent()) {
            duplicates.increment();
            log.info("inquiry.duplicate reference={} client_request_id={}",
                    alreadyStored.get().getPublicId(), metadata.clientRequestId());
            return InquirySubmission.replayed(alreadyStored.get());
        }

        try {
            Inquiry inquiry = transactionTemplate.execute(status -> {
                Inquiry entity = repository.saveAndFlush(Inquiry.create(request, metadata));
                // Published inside the transaction: the listener fires only after the commit,
                // so a rollback can never trigger an export.
                events.publishEvent(new InquiryCreatedEvent(entity.getPublicId()));
                return entity;
            });
            if (inquiry == null) {
                throw new IllegalStateException("Transaction returned no result for an accepted inquiry");
            }
            accepted.increment();
            log.info("inquiry.accepted reference={} name={} email={} phone={} message={} ip_fp={}",
                    inquiry.getPublicId(),
                    PiiMasker.maskName(inquiry.getName()),
                    PiiMasker.maskEmail(inquiry.getEmail()),
                    PiiMasker.maskPhone(inquiry.getPhone()),
                    PiiMasker.describeLength(inquiry.getMessage()),
                    metadata.sourceIpHash());
            return InquirySubmission.created(inquiry);
        } catch (DataIntegrityViolationException exception) {
            // Two requests with the same idempotency key raced: the loser reports the winner's row.
            duplicates.increment();
            Inquiry winner = repository.findByClientRequestId(metadata.clientRequestId())
                    .orElseThrow(() -> exception);
            log.info("inquiry.duplicate.race reference={} client_request_id={}",
                    winner.getPublicId(), metadata.clientRequestId());
            return InquirySubmission.replayed(winner);
        }
    }
}
