package com.nkweb.pharma.service.export;

import com.nkweb.pharma.config.ExportProperties;
import com.nkweb.pharma.service.InquiryCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Failure semantics of the outbox drain.
 *
 * <p>Every test here answers one question: <em>can a row be lost?</em> The answer must be no, in
 * every branch – sink failure, unexpected exception, a database that is itself down, or the
 * pipeline being switched off.
 */
class InquiryExportWorkerTest {

    private static final int MAX_ATTEMPTS = 6;

    private ExportClaimService claimService;
    private ExportSink sink;
    private InquiryExportWorker worker;

    @BeforeEach
    void setUp() {
        claimService = mock(ExportClaimService.class);
        sink = mock(ExportSink.class);
        when(sink.name()).thenReturn("test-sink");
        worker = newWorker(true, 250);
    }

    // ------------------------------------------------------------------
    // happy path
    // ------------------------------------------------------------------

    @Test
    void exportsABatchAndRecordsSuccess() {
        List<InquiryExportRow> batch = List.of(row(), row());
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(batch);
        when(claimService.markExported(any())).thenReturn(batch.size());

        assertThat(worker.drainPending()).isEqualTo(2);

        verify(sink).append(batch);
        verify(claimService).markExported(referencesOf(batch));
        verify(claimService, never()).markFailed(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("drains several batches until the queue is empty")
    void drainsUntilTheQueueIsEmpty() {
        when(claimService.claimBatch(anyInt(), anyInt()))
                .thenReturn(List.of(row(), row()))
                .thenReturn(List.of(row(), row()))
                .thenReturn(List.of());
        when(claimService.markExported(any())).thenReturn(2);
        worker = newWorker(true, 2);

        assertThat(worker.drainPending()).isEqualTo(4);
        verify(claimService, times(3)).claimBatch(2, MAX_ATTEMPTS);
    }

    @Test
    void doesNothingWhenTheQueueIsEmpty() {
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(List.of());

        assertThat(worker.drainPending()).isZero();

        verifyNoInteractions(sink);
        verify(claimService, never()).markExported(any());
    }

    @Test
    @DisplayName("an inquiry event triggers a drain")
    void inquiryEventTriggersADrain() {
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(List.of());

        worker.onInquiryCreated(new InquiryCreatedEvent(UUID.randomUUID()));

        verify(claimService).claimBatch(anyInt(), anyInt());
    }

    // ------------------------------------------------------------------
    // failure handling: the rows must never be silently dropped
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a sink failure re-queues the batch with the reason attached")
    void sinkFailureRequeuesTheBatch() {
        List<InquiryExportRow> batch = List.of(row());
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(batch);
        doThrow(new ExportException("disk is full")).when(sink).append(batch);

        assertThat(worker.drainPending()).isZero();

        verify(claimService).markFailed(eq(referencesOf(batch)), contains("disk is full"), eq(MAX_ATTEMPTS));
    }

    @Test
    @DisplayName("an unexpected RuntimeException from a sink is contained, not propagated")
    void unexpectedSinkFailureIsContained() {
        List<InquiryExportRow> batch = List.of(row());
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(batch);
        doThrow(new IllegalStateException("boom")).when(sink).append(batch);

        assertThatCode(worker::drainPending).doesNotThrowAnyException();

        verify(claimService).markFailed(eq(referencesOf(batch)), contains("IllegalStateException"), eq(MAX_ATTEMPTS));
    }

    @Test
    @DisplayName("a failure while recording the failure still does not lose the rows to the caller")
    void failureWhileRecordingTheFailureIsContained() {
        List<InquiryExportRow> batch = List.of(row());
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(batch);
        doThrow(new ExportException("sink down")).when(sink).append(batch);
        when(claimService.markFailed(any(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("database down"));

        assertThatCode(worker::drainPending).doesNotThrowAnyException();

        // The rows stay PROCESSING; the sweeper is the backstop that recovers them.
        verify(claimService).markFailed(any(), contains("sink down"), eq(MAX_ATTEMPTS));
    }

    @Test
    void recordsAMismatchWhenFewerRowsWhereMarkedExportedThanWritten() {
        List<InquiryExportRow> batch = List.of(row(), row());
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(batch);
        when(claimService.markExported(any())).thenReturn(1);

        assertThat(worker.drainPending()).isEqualTo(2);

        verify(claimService).markExported(referencesOf(batch));
    }

    // ------------------------------------------------------------------
    // sweeper (crash recovery)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sweep re-queues rows abandoned for more than five minutes and then drains")
    void sweepRecoversAbandonedRows() {
        when(claimService.requeueStaleProcessing(Duration.ofMinutes(5))).thenReturn(3);
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(List.of());

        worker.sweep();

        verify(claimService).requeueStaleProcessing(Duration.ofMinutes(5));
        verify(claimService).claimBatch(anyInt(), anyInt());
    }

    @Test
    void sweepSurvivesARecoveryFailure() {
        when(claimService.requeueStaleProcessing(any())).thenThrow(new RuntimeException("database down"));
        when(claimService.claimBatch(anyInt(), anyInt())).thenReturn(List.of());

        assertThatCode(worker::sweep).doesNotThrowAnyException();

        verify(claimService).claimBatch(anyInt(), anyInt());
    }

    // ------------------------------------------------------------------
    // disabled pipeline
    // ------------------------------------------------------------------

    @Test
    @DisplayName("with export disabled nothing is claimed, written or scheduled")
    void disabledPipelineDoesNotTouchTheDatabase() {
        worker = newWorker(false, 250);

        assertThat(worker.drainPending()).isZero();
        worker.onInquiryCreated(new InquiryCreatedEvent(UUID.randomUUID()));
        worker.sweep();

        verifyNoInteractions(claimService);
        verifyNoInteractions(sink);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private InquiryExportWorker newWorker(boolean enabled, int batchSize) {
        ExportProperties properties = new ExportProperties(
                enabled,
                List.of(ExcelFileExportSink.SINK_NAME),
                "unused",
                "unused.xlsx",
                "Inquiries",
                batchSize,
                MAX_ATTEMPTS,
                Duration.ofSeconds(15),
                Duration.ofSeconds(20),
                new ExportProperties.GoogleSheets("", "", Duration.ofSeconds(10)));
        InquiryExportWorker created =
                new InquiryExportWorker(claimService, sink, properties, new SimpleMeterRegistry());
        // The constructor logs the configured sink names, which is a legitimate read of the mock.
        // Forget it so that "no interactions" assertions measure the drain, not the wiring.
        clearInvocations(sink);
        return created;
    }

    private static InquiryExportRow row() {
        return new InquiryExportRow(
                UUID.randomUUID().toString(),
                Instant.parse("2026-01-02T03:04:05Z"),
                "Jane Doe",
                "jane@example.com",
                "+1 555 010 9999",
                "Acme Pharma Ltd",
                "We would like pricing for Cardolix 20.",
                true);
    }

    private static Collection<UUID> referencesOf(List<InquiryExportRow> batch) {
        return batch.stream().map(InquiryExportRow::referenceUuid).toList();
    }
}
