package com.wpanther.invoice.pdf.application.service;

import com.wpanther.invoice.pdf.application.port.out.DocumentArchivePort;
import com.wpanther.invoice.pdf.application.port.out.PdfEventPort;
import com.wpanther.invoice.pdf.application.port.out.SagaReplyPort;
import com.wpanther.invoice.pdf.application.dto.event.InvoicePdfGeneratedEvent;
import com.wpanther.invoice.pdf.domain.model.GenerationStatus;
import com.wpanther.invoice.pdf.domain.model.InvoicePdfDocument;
import com.wpanther.invoice.pdf.domain.repository.InvoicePdfDocumentRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoicePdfDocumentService Unit Tests")
class InvoicePdfDocumentServiceTest {

    @Mock private InvoicePdfDocumentRepository repository;
    @Mock private PdfEventPort pdfEventPort;
    @Mock private SagaReplyPort sagaReplyPort;
    @Mock private DocumentArchivePort documentArchivePort;

    @InjectMocks
    private InvoicePdfDocumentService service;

    private static final UUID DOC_ID = UUID.randomUUID();
    private static final String S3_KEY  = "2024/01/15/invoice-INV-001-uuid.pdf";
    private static final String FILE_URL = "http://localhost:9001/invoices/" + S3_KEY;
    private static final String SAGA_ID = "saga-001";
    private static final String CORR_ID = "corr-456";
    private static final SagaStep SAGA_STEP = SagaStep.GENERATE_INVOICE_PDF;

    private InvoicePdfDocument generatingDoc() {
        return InvoicePdfDocument.builder()
                .id(DOC_ID).invoiceId("doc-123").invoiceNumber("INV-001")
                .status(GenerationStatus.GENERATING)
                .retryCount(0)
                .build();
    }

    // -------------------------------------------------------------------------
    // findByInvoiceId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByInvoiceId() delegates to repository and returns the result")
    void findByInvoiceId_delegatesToRepository() {
        InvoicePdfDocument doc = generatingDoc();
        when(repository.findByInvoiceId("doc-123")).thenReturn(Optional.of(doc));

        Optional<InvoicePdfDocument> result = service.findByInvoiceId("doc-123");

        assertThat(result).isPresent().contains(doc);
        verify(repository).findByInvoiceId("doc-123");
    }

    @Test
    @DisplayName("findByInvoiceId() returns empty when no document exists")
    void findByInvoiceId_returnsEmpty_whenNotFound() {
        when(repository.findByInvoiceId("unknown")).thenReturn(Optional.empty());

        Optional<InvoicePdfDocument> result = service.findByInvoiceId("unknown");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // beginGeneration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("beginGeneration() creates GENERATING document with single save")
    void beginGeneration_savesOnce_inGeneratingState() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InvoicePdfDocument result = service.beginGeneration("doc-123", "INV-001");

        assertThat(result.getStatus()).isEqualTo(GenerationStatus.GENERATING);
        assertThat(result.getInvoiceId()).isEqualTo("doc-123");
        verify(repository, times(1)).save(any());
    }

    // -------------------------------------------------------------------------
    // completeGenerationAndPublish
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("completeGenerationAndPublish() marks COMPLETED and publishes both events")
    void completeGenerationAndPublish_marksCompletedAndPublishes() {
        InvoicePdfDocument doc = generatingDoc();
        when(repository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.completeGenerationAndPublish(DOC_ID, S3_KEY, FILE_URL, 5000L, -1,
                "doc-123", "INV-001", SAGA_ID, SAGA_STEP, CORR_ID);

        assertThat(doc.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(doc.getDocumentPath()).isEqualTo(S3_KEY);
        assertThat(doc.getFileSize()).isEqualTo(5000L);
        assertThat(doc.isXmlEmbedded()).isTrue();
        verify(pdfEventPort).publishPdfGenerated(any(InvoicePdfGeneratedEvent.class));
        verify(sagaReplyPort).publishSuccess(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID),
                eq(FILE_URL), eq(5000L));
    }

    @Test
    @DisplayName("completeGenerationAndPublish() carries forward retry count when previousRetryCount >= 0")
    void completeGenerationAndPublish_carriesForwardRetryCount() {
        InvoicePdfDocument doc = generatingDoc();
        when(repository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.completeGenerationAndPublish(DOC_ID, S3_KEY, FILE_URL, 1000L, 1,
                "doc-123", "INV-001", SAGA_ID, SAGA_STEP, CORR_ID);

        // previousRetryCount=1 → target=2
        assertThat(doc.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("completeGenerationAndPublish() does not change retryCount when previousRetryCount is -1")
    void completeGenerationAndPublish_noRetryCountWhenFirstAttempt() {
        InvoicePdfDocument doc = generatingDoc();
        when(repository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.completeGenerationAndPublish(DOC_ID, S3_KEY, FILE_URL, 1000L, -1,
                "doc-123", "INV-001", SAGA_ID, SAGA_STEP, CORR_ID);

        assertThat(doc.getRetryCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // failGenerationAndPublish
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("failGenerationAndPublish() marks FAILED, persists retry count, publishes FAILURE")
    void failGenerationAndPublish_marksFailedAndPublishes() {
        InvoicePdfDocument doc = generatingDoc();
        when(repository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.failGenerationAndPublish(DOC_ID, "FOP failed", 1, SAGA_ID, SAGA_STEP, CORR_ID);

        assertThat(doc.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(doc.getRetryCount()).isEqualTo(2); // 1+1
        verify(sagaReplyPort).publishFailure(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), eq("FOP failed"));
        verify(pdfEventPort, never()).publishPdfGenerated(any());
    }

    // -------------------------------------------------------------------------
    // Reply publishers
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("publishIdempotentSuccess() publishes both events without touching repository")
    void publishIdempotentSuccess_publishesWithoutSave() {
        InvoicePdfDocument existing = InvoicePdfDocument.builder()
                .id(DOC_ID).invoiceId("doc-123").invoiceNumber("INV-001")
                .status(GenerationStatus.COMPLETED)
                .documentUrl(FILE_URL).fileSize(9000L).xmlEmbedded(true)
                .build();

        service.publishIdempotentSuccess(existing, "doc-123", "INV-001", SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfEventPort).publishPdfGenerated(any());
        verify(sagaReplyPort).publishSuccess(eq(SAGA_ID), any(), eq(CORR_ID),
                eq(FILE_URL), eq(9000L));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("publishRetryExhausted() publishes FAILURE reply without touching repository")
    void publishRetryExhausted_publishesFailure() {
        service.publishRetryExhausted(SAGA_ID, SAGA_STEP, CORR_ID, "doc-123", "INV-001");

        verify(sagaReplyPort).publishFailure(eq(SAGA_ID), any(), eq(CORR_ID),
                contains("Maximum retry attempts exceeded"));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("publishGenerationFailure() publishes FAILURE reply")
    void publishGenerationFailure_publishesFailure() {
        service.publishGenerationFailure(SAGA_ID, SAGA_STEP, CORR_ID, "signedXmlUrl is null");

        verify(sagaReplyPort).publishFailure(eq(SAGA_ID), any(), eq(CORR_ID), eq("signedXmlUrl is null"));
    }

    @Test
    @DisplayName("publishCompensated() publishes COMPENSATED reply")
    void publishCompensated_publishes() {
        service.publishCompensated(SAGA_ID, SAGA_STEP, CORR_ID);

        verify(sagaReplyPort).publishCompensated(eq(SAGA_ID), any(), eq(CORR_ID));
    }

    @Test
    @DisplayName("publishCompensationFailure() publishes FAILURE reply")
    void publishCompensationFailure_publishes() {
        service.publishCompensationFailure(SAGA_ID, SAGA_STEP, CORR_ID, "Compensation failed: S3 error");

        verify(sagaReplyPort).publishFailure(eq(SAGA_ID), any(), eq(CORR_ID),
                eq("Compensation failed: S3 error"));
    }

    @Test
    @DisplayName("completeGenerationAndPublish() throws IllegalStateException when document not found")
    void completeGenerationAndPublish_documentNotFound_throwsIllegalStateException() {
        when(repository.findById(DOC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.completeGenerationAndPublish(DOC_ID, S3_KEY, FILE_URL, 1000L, -1,
                        "doc-123", "INV-001", SAGA_ID, SAGA_STEP, CORR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal state error");
    }

    @Test
    @DisplayName("failGenerationAndPublish() uses fallback message when errorMessage is null")
    void failGenerationAndPublish_nullErrorMessage_usesFallback() {
        InvoicePdfDocument doc = generatingDoc();
        when(repository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.failGenerationAndPublish(DOC_ID, null, -1, SAGA_ID, SAGA_STEP, CORR_ID);

        assertThat(doc.getErrorMessage()).isEqualTo("PDF generation failed");
        verify(sagaReplyPort).publishFailure(eq(SAGA_ID), any(), eq(CORR_ID),
                eq("PDF generation failed"));
    }

    // -------------------------------------------------------------------------
    // replaceAndBeginGeneration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("replaceAndBeginGeneration() deletes, flushes, creates GENERATING doc with advanced retry count")
    void replaceAndBeginGeneration_deletesAndCreatesNewDocument() {
        UUID existingId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InvoicePdfDocument result = service.replaceAndBeginGeneration(existingId, 1, "doc-123", "INV-001");

        verify(repository).deleteById(existingId);
        verify(repository).flush();
        verify(repository).save(any());
        assertThat(result.getStatus()).isEqualTo(GenerationStatus.GENERATING);
        assertThat(result.getRetryCount()).isEqualTo(2); // previousRetryCount(1) + 1
        assertThat(result.getInvoiceId()).isEqualTo("doc-123");
        assertThat(result.getInvoiceNumber()).isEqualTo("INV-001");
    }

    // -------------------------------------------------------------------------
    // deleteById
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteById() deletes and flushes")
    void deleteById_deletesAndFlushes() {
        service.deleteById(DOC_ID);

        verify(repository).deleteById(DOC_ID);
        verify(repository).flush();
    }
}
