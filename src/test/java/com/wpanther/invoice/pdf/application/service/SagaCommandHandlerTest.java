package com.wpanther.invoice.pdf.application.service;

import com.wpanther.invoice.pdf.application.port.out.PdfStoragePort;
import com.wpanther.invoice.pdf.application.port.out.SagaReplyPort;
import com.wpanther.invoice.pdf.application.port.out.SignedXmlFetchPort;
import com.wpanther.invoice.pdf.domain.model.GenerationStatus;
import com.wpanther.invoice.pdf.domain.model.InvoicePdfDocument;
import com.wpanther.invoice.pdf.domain.exception.InvoicePdfGenerationException;
import com.wpanther.invoice.pdf.domain.service.InvoicePdfGenerationService;
import com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka.SagaCommandHandler;
import com.wpanther.saga.domain.enums.SagaStep;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaCommandHandler Unit Tests")
class SagaCommandHandlerTest {

    @Mock private InvoicePdfDocumentService pdfDocumentService;
    @Mock private InvoicePdfGenerationService pdfGenerationService;
    @Mock private PdfStoragePort pdfStoragePort;
    @Mock private SagaReplyPort sagaReplyPort;
    @Mock private SignedXmlFetchPort signedXmlFetchPort;

    private SagaCommandHandler sagaCommandHandler;

    private static final String DOC_ID         = "doc-123";
    private static final String DOC_NUMBER     = "INV-2024-001";
    private static final String SAGA_ID        = "saga-001";
    private static final String CORR_ID        = "corr-456";
    private static final SagaStep SAGA_STEP    = SagaStep.GENERATE_INVOICE_PDF;
    private static final String SIGNED_XML_URL = "http://minio:9000/signed/invoice-signed.xml";
    private static final String SIGNED_XML_CONTENT = "<Invoice>signed</Invoice>";
    private static final String S3_KEY         = "2024/01/15/invoice-INV-2024-001-uuid.pdf";
    private static final String FILE_URL       = "http://localhost:9001/invoices/" + S3_KEY;

    @BeforeEach
    void setUp() {
        sagaCommandHandler = new SagaCommandHandler(
                pdfDocumentService, pdfGenerationService,
                pdfStoragePort, sagaReplyPort, signedXmlFetchPort, 3);
    }

    private InvoicePdfDocument generatingDoc() {
        return InvoicePdfDocument.builder()
                .id(UUID.randomUUID()).invoiceId(DOC_ID).invoiceNumber(DOC_NUMBER)
                .status(GenerationStatus.GENERATING).retryCount(0)
                .build();
    }

    private InvoicePdfDocument completedDoc() {
        return InvoicePdfDocument.builder()
                .id(UUID.randomUUID()).invoiceId(DOC_ID).invoiceNumber(DOC_NUMBER)
                .status(GenerationStatus.COMPLETED)
                .documentPath(S3_KEY).documentUrl(FILE_URL).fileSize(12345L)
                .xmlEmbedded(true).retryCount(0)
                .build();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Happy path: beginGeneration → fetch → generate → upload → completeGenerationAndPublish")
    void handleProcessCommand_success() throws Exception {
        byte[] pdfBytes = new byte[5000];
        InvoicePdfDocument doc = generatingDoc();

        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL)).thenReturn(SIGNED_XML_CONTENT);
        when(pdfGenerationService.generatePdf(anyString(), anyString())).thenReturn(pdfBytes);
        when(pdfStoragePort.store(anyString(), any())).thenReturn(S3_KEY);
        when(pdfStoragePort.resolveUrl(S3_KEY)).thenReturn(FILE_URL);
        when(pdfDocumentService.beginGeneration(DOC_ID, DOC_NUMBER)).thenReturn(doc);

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).beginGeneration(DOC_ID, DOC_NUMBER);
        verify(pdfGenerationService).generatePdf(DOC_NUMBER, SIGNED_XML_CONTENT);
        verify(pdfStoragePort).store(DOC_NUMBER, pdfBytes);
        verify(pdfDocumentService).completeGenerationAndPublish(
                eq(doc.getId()), eq(S3_KEY), eq(FILE_URL), eq(5000L), eq(-1),
                eq(DOC_ID), eq(DOC_NUMBER), eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Idempotency: already COMPLETED → publishIdempotentSuccess, no generation")
    void handleProcessCommand_alreadyCompleted() throws Exception {
        InvoicePdfDocument completed = completedDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.of(completed));

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishIdempotentSuccess(
                eq(completed), eq(DOC_ID), eq(DOC_NUMBER), eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
        verify(pdfDocumentService, never()).beginGeneration(anyString(), anyString());
        verifyNoInteractions(pdfGenerationService, pdfStoragePort, signedXmlFetchPort);
    }

    // -------------------------------------------------------------------------
    // Retry logic
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Max retries exceeded → publishRetryExhausted, no generation")
    void handleProcessCommand_maxRetriesExceeded() {
        InvoicePdfDocument failed = InvoicePdfDocument.builder()
                .id(UUID.randomUUID()).invoiceId(DOC_ID).invoiceNumber(DOC_NUMBER)
                .status(GenerationStatus.FAILED).retryCount(3).build();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.of(failed));

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishRetryExhausted(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), eq(DOC_ID), eq(DOC_NUMBER));
        verify(pdfDocumentService, never()).beginGeneration(anyString(), anyString());
        verifyNoInteractions(signedXmlFetchPort);
    }

    @Test
    @DisplayName("Retry below max: replaceAndBeginGeneration (atomic) + completeGenerationAndPublish")
    void handleProcessCommand_retryBelowMax() throws Exception {
        byte[] pdfBytes = new byte[1000];
        UUID failedId = UUID.randomUUID();
        InvoicePdfDocument failed = InvoicePdfDocument.builder()
                .id(failedId).invoiceId(DOC_ID).invoiceNumber(DOC_NUMBER)
                .status(GenerationStatus.FAILED).retryCount(1).build();
        InvoicePdfDocument newDoc = generatingDoc();

        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.of(failed));
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL)).thenReturn(SIGNED_XML_CONTENT);
        when(pdfGenerationService.generatePdf(anyString(), anyString())).thenReturn(pdfBytes);
        when(pdfStoragePort.store(anyString(), any())).thenReturn(S3_KEY);
        when(pdfStoragePort.resolveUrl(S3_KEY)).thenReturn(FILE_URL);
        when(pdfDocumentService.replaceAndBeginGeneration(eq(failedId), eq(1), eq(DOC_ID), eq(DOC_NUMBER)))
                .thenReturn(newDoc);

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).replaceAndBeginGeneration(failedId, 1, DOC_ID, DOC_NUMBER);
        verify(pdfDocumentService, never()).deleteById(any());
        verify(pdfDocumentService, never()).beginGeneration(anyString(), anyString());
        verify(pdfDocumentService).completeGenerationAndPublish(
                eq(newDoc.getId()), any(), any(), anyLong(), eq(1),
                eq(DOC_ID), eq(DOC_NUMBER), eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    // -------------------------------------------------------------------------
    // Stuck GENERATING state (TX2 rolled back)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Stuck GENERATING (TX2 rolled back), retries below max → replaceAndBeginGeneration (atomic) + retry")
    void handleProcessCommand_stuckGenerating_retriesNotExceeded() throws Exception {
        byte[] pdfBytes = new byte[1000];
        UUID stuckId = UUID.randomUUID();
        InvoicePdfDocument stuck = InvoicePdfDocument.builder()
                .id(stuckId).invoiceId(DOC_ID).invoiceNumber(DOC_NUMBER)
                .status(GenerationStatus.GENERATING).retryCount(1).build();
        InvoicePdfDocument newDoc = generatingDoc();

        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.of(stuck));
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL)).thenReturn(SIGNED_XML_CONTENT);
        when(pdfGenerationService.generatePdf(anyString(), anyString())).thenReturn(pdfBytes);
        when(pdfStoragePort.store(anyString(), any())).thenReturn(S3_KEY);
        when(pdfStoragePort.resolveUrl(S3_KEY)).thenReturn(FILE_URL);
        when(pdfDocumentService.replaceAndBeginGeneration(eq(stuckId), eq(1), eq(DOC_ID), eq(DOC_NUMBER)))
                .thenReturn(newDoc);

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).replaceAndBeginGeneration(stuckId, 1, DOC_ID, DOC_NUMBER);
        verify(pdfDocumentService, never()).deleteById(any());
        verify(pdfDocumentService, never()).beginGeneration(anyString(), anyString());
        verify(pdfDocumentService).completeGenerationAndPublish(
                eq(newDoc.getId()), any(), any(), anyLong(), eq(1),
                eq(DOC_ID), eq(DOC_NUMBER), eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
        verify(pdfDocumentService, never()).publishRetryExhausted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Stuck GENERATING with max retries exceeded → publishRetryExhausted, no generation")
    void handleProcessCommand_stuckGenerating_maxRetriesExceeded() {
        InvoicePdfDocument stuck = InvoicePdfDocument.builder()
                .id(UUID.randomUUID()).invoiceId(DOC_ID).invoiceNumber(DOC_NUMBER)
                .status(GenerationStatus.GENERATING).retryCount(3).build();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.of(stuck));

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishRetryExhausted(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), eq(DOC_ID), eq(DOC_NUMBER));
        verify(pdfDocumentService, never()).deleteById(any());
        verify(pdfDocumentService, never()).beginGeneration(anyString(), anyString());
        verifyNoInteractions(signedXmlFetchPort);
    }

    // -------------------------------------------------------------------------
    // Failure paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Fetch returns blank content → failGenerationAndPublish after beginGeneration")
    void handleProcessCommand_blankFetchedXml() {
        InvoicePdfDocument doc = generatingDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());
        when(pdfDocumentService.beginGeneration(anyString(), anyString())).thenReturn(doc);
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL))
                .thenThrow(new SignedXmlFetchPort.SignedXmlFetchException(
                        "Failed to download signed XML from " + SIGNED_XML_URL));

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).failGenerationAndPublish(
                eq(doc.getId()), contains("Failed to download signed XML"), eq(-1),
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    @Test
    @DisplayName("Blank documentNumber → publishGenerationFailure before beginGeneration")
    void handleProcessCommand_blankDocumentNumber() {
        sagaCommandHandler.handle(DOC_ID, "   ", SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishGenerationFailure(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), contains("documentNumber"));
        verify(pdfDocumentService, never()).beginGeneration(anyString(), anyString());
        verifyNoInteractions(signedXmlFetchPort);
    }

    @Test
    @DisplayName("Blank documentId → publishGenerationFailure before beginGeneration")
    void handleProcessCommand_blankDocumentId() {
        sagaCommandHandler.handle("   ", DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishGenerationFailure(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), contains("documentId"));
        verify(pdfDocumentService, never()).beginGeneration(anyString(), anyString());
        verifyNoInteractions(signedXmlFetchPort);
    }

    @Test
    @DisplayName("Blank signedXmlUrl → publishGenerationFailure before beginGeneration")
    void handleProcessCommand_blankSignedXmlUrl() {
        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, "  ", SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishGenerationFailure(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), contains("signedXmlUrl"));
        verify(pdfDocumentService, never()).beginGeneration(anyString(), anyString());
        verifyNoInteractions(signedXmlFetchPort);
    }

    @Test
    @DisplayName("HTTP fetch throws → failGenerationAndPublish called")
    void handleProcessCommand_fetchFails() {
        InvoicePdfDocument doc = generatingDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL))
                .thenThrow(new SignedXmlFetchPort.SignedXmlFetchException(
                        "Failed to download signed XML from " + SIGNED_XML_URL,
                        new RuntimeException("Connection refused")));
        when(pdfDocumentService.beginGeneration(anyString(), anyString())).thenReturn(doc);

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).failGenerationAndPublish(
                eq(doc.getId()), contains("Failed to download signed XML"), eq(-1),
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    @Test
    @DisplayName("PDF generation throws → failGenerationAndPublish called")
    void handleProcessCommand_pdfGenerationFails() throws Exception {
        InvoicePdfDocument doc = generatingDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL)).thenReturn(SIGNED_XML_CONTENT);
        when(pdfGenerationService.generatePdf(anyString(), anyString()))
                .thenThrow(new InvoicePdfGenerationException("FOP failed"));
        when(pdfDocumentService.beginGeneration(anyString(), anyString())).thenReturn(doc);

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).failGenerationAndPublish(
                eq(doc.getId()), contains("FOP failed"), eq(-1),
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
        verify(pdfDocumentService, never()).completeGenerationAndPublish(any(), any(), any(), anyLong(), anyInt(),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("MinIO upload throws → failGenerationAndPublish called")
    void handleProcessCommand_minioUploadFails() throws Exception {
        InvoicePdfDocument doc = generatingDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL)).thenReturn(SIGNED_XML_CONTENT);
        when(pdfGenerationService.generatePdf(anyString(), anyString())).thenReturn(new byte[1000]);
        when(pdfStoragePort.store(anyString(), any())).thenThrow(new RuntimeException("MinIO unavailable"));
        when(pdfDocumentService.beginGeneration(anyString(), anyString())).thenReturn(doc);

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).failGenerationAndPublish(
                eq(doc.getId()), contains("MinIO"), eq(-1),
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    @Test
    @DisplayName("Upload succeeds but DB write fails → delete orphaned MinIO object + failGenerationAndPublish")
    void handleProcessCommand_dbWriteFailsAfterUpload_deletesOrphanedObject() throws Exception {
        InvoicePdfDocument doc = generatingDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());
        when(pdfDocumentService.beginGeneration(anyString(), anyString())).thenReturn(doc);
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL)).thenReturn(SIGNED_XML_CONTENT);
        when(pdfGenerationService.generatePdf(anyString(), anyString())).thenReturn(new byte[1000]);
        when(pdfStoragePort.store(anyString(), any())).thenReturn(S3_KEY);
        when(pdfStoragePort.resolveUrl(S3_KEY)).thenReturn(FILE_URL);
        doThrow(new RuntimeException("DB connection lost"))
                .when(pdfDocumentService).completeGenerationAndPublish(any(), any(), any(), anyLong(), anyInt(),
                        any(), any(), any(), any(), any());

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfStoragePort).delete(S3_KEY);
        verify(pdfDocumentService).failGenerationAndPublish(eq(doc.getId()), any(), anyInt(),
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    @Test
    @DisplayName("Upload succeeds, DB write fails, MinIO delete also fails → ORPHAN_PDF logged + failGenerationAndPublish")
    void handleProcessCommand_dbWriteFailsAfterUpload_minioDeleteAlsoFails() throws Exception {
        InvoicePdfDocument doc = generatingDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());
        when(pdfDocumentService.beginGeneration(anyString(), anyString())).thenReturn(doc);
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL)).thenReturn(SIGNED_XML_CONTENT);
        when(pdfGenerationService.generatePdf(anyString(), anyString())).thenReturn(new byte[1000]);
        when(pdfStoragePort.store(anyString(), any())).thenReturn(S3_KEY);
        when(pdfStoragePort.resolveUrl(S3_KEY)).thenReturn(FILE_URL);
        doThrow(new RuntimeException("DB connection lost"))
                .when(pdfDocumentService).completeGenerationAndPublish(any(), any(), any(), anyLong(), anyInt(),
                        any(), any(), any(), any(), any());
        doThrow(new RuntimeException("MinIO also down")).when(pdfStoragePort).delete(anyString());

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfStoragePort).delete(S3_KEY);
        verify(pdfDocumentService).failGenerationAndPublish(eq(doc.getId()), any(), anyInt(),
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    @Test
    @DisplayName("Unexpected exception in outer handler → publishGenerationFailure called")
    void handleProcessCommand_unexpectedExceptionInOuterBlock_publishesFailure() {
        when(pdfDocumentService.findByInvoiceId(DOC_ID))
                .thenThrow(new RuntimeException("Unexpected infrastructure failure"));

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishGenerationFailure(
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), contains("Unexpected infrastructure failure"));
    }

    // -------------------------------------------------------------------------
    // handleCompensation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Compensation: deleteById + pdfStoragePort.delete + publishCompensated")
    void handleCompensation_success() {
        InvoicePdfDocument doc = completedDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.of(doc));

        sagaCommandHandler.handle(DOC_ID, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).deleteById(doc.getId());
        verify(pdfStoragePort).delete(doc.getDocumentPath());
        verify(pdfDocumentService).publishCompensated(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    @Test
    @DisplayName("Compensation with no document → publishCompensated only (idempotent)")
    void handleCompensation_noDocument() {
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());

        sagaCommandHandler.handle(DOC_ID, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService, never()).deleteById(any());
        verifyNoInteractions(pdfStoragePort);
        verify(pdfDocumentService).publishCompensated(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    @Test
    @DisplayName("MinIO delete failure during compensation is swallowed; publishCompensated still called")
    void handleCompensation_minioDeleteFails_stillPublishesCompensated() {
        InvoicePdfDocument doc = completedDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.of(doc));
        doThrow(new RuntimeException("MinIO error")).when(pdfStoragePort).delete(anyString());

        sagaCommandHandler.handle(DOC_ID, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).deleteById(doc.getId());
        verify(pdfDocumentService).publishCompensated(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
    }

    @Test
    @DisplayName("deleteById throws during compensation → publishCompensationFailure")
    void handleCompensation_dbDeleteFails() {
        InvoicePdfDocument doc = completedDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.of(doc));
        doThrow(new RuntimeException("DB error")).when(pdfDocumentService).deleteById(any());

        sagaCommandHandler.handle(DOC_ID, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishCompensationFailure(eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), contains("Compensation failed"));
        verify(pdfDocumentService, never()).publishCompensated(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // publishOrchestrationFailure (fully-parsed process command → DLQ)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("publishOrchestrationFailure: publishes FAILURE reply citing retry exhaustion")
    void publishOrchestrationFailure_publishesFailure() {
        sagaCommandHandler.publishOrchestrationFailure(
                SAGA_ID, SAGA_STEP, CORR_ID, DOC_ID, DOC_NUMBER,
                new RuntimeException("processing blew up"));

        verify(sagaReplyPort).publishFailure(
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID),
                contains("processing blew up"));
    }

    @Test
    @DisplayName("publishOrchestrationFailure: swallows port exception so Camel DLQ routing continues")
    void publishOrchestrationFailure_sagaReplyThrows_doesNotPropagate() {
        doThrow(new RuntimeException("outbox write failed"))
                .when(sagaReplyPort).publishFailure(anyString(), any(), anyString(), anyString());

        sagaCommandHandler.publishOrchestrationFailure(
                SAGA_ID, SAGA_STEP, CORR_ID, DOC_ID, DOC_NUMBER,
                new RuntimeException("cause"));
    }

    // -------------------------------------------------------------------------
    // publishCompensationOrchestrationFailure
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("publishCompensationOrchestrationFailure: publishes FAILURE reply citing retry exhaustion")
    void publishCompensationOrchestrationFailure_publishesFailure() {
        sagaCommandHandler.publishCompensationOrchestrationFailure(
                SAGA_ID, SAGA_STEP, CORR_ID, DOC_ID,
                new RuntimeException("compensation blew up"));

        verify(sagaReplyPort).publishFailure(
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID),
                contains("compensation blew up"));
    }

    @Test
    @DisplayName("publishCompensationOrchestrationFailure: swallows port exception so DLQ routing continues")
    void publishCompensationOrchestrationFailure_sagaReplyThrows_doesNotPropagate() {
        doThrow(new RuntimeException("outbox write failed"))
                .when(sagaReplyPort).publishFailure(anyString(), any(), anyString(), anyString());

        sagaCommandHandler.publishCompensationOrchestrationFailure(
                SAGA_ID, SAGA_STEP, CORR_ID, DOC_ID,
                new RuntimeException("cause"));
    }

    // -------------------------------------------------------------------------
    // publishOrchestrationFailureForUnparsedMessage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("publishOrchestrationFailureForUnparsedMessage: publishes FAILURE reply describing deserialization error")
    void publishOrchestrationFailureForUnparsedMessage_publishesFailure() {
        Throwable cause = new RuntimeException("Unrecognized field: unknownStep");

        sagaCommandHandler.publishOrchestrationFailureForUnparsedMessage(
                SAGA_ID, SAGA_STEP, CORR_ID, cause);

        verify(sagaReplyPort).publishFailure(
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID),
                contains("deserialization failure"));
    }

    @Test
    @DisplayName("publishOrchestrationFailureForUnparsedMessage: swallows port exception so Camel DLQ routing continues")
    void publishOrchestrationFailureForUnparsedMessage_sagaReplyThrows_doesNotPropagate() {
        doThrow(new RuntimeException("outbox write failed"))
                .when(sagaReplyPort).publishFailure(anyString(), any(), anyString(), anyString());

        sagaCommandHandler.publishOrchestrationFailureForUnparsedMessage(
                SAGA_ID, SAGA_STEP, CORR_ID,
                new RuntimeException("cause"));
    }

    // -------------------------------------------------------------------------
    // Circuit-breaker-open path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MinIO circuit breaker OPEN on upload → failGenerationAndPublish, delete NOT called")
    void handleProcessCommand_circuitBreakerOpen_failsWithoutDelete() throws Exception {
        InvoicePdfDocument doc = generatingDoc();
        when(pdfDocumentService.findByInvoiceId(DOC_ID)).thenReturn(Optional.empty());
        when(pdfDocumentService.beginGeneration(anyString(), anyString())).thenReturn(doc);
        when(signedXmlFetchPort.fetch(SIGNED_XML_URL)).thenReturn(SIGNED_XML_CONTENT);
        when(pdfGenerationService.generatePdf(anyString(), anyString()))
                .thenReturn(new byte[]{1, 2, 3});
        when(pdfStoragePort.store(anyString(), any()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("minio")));

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).failGenerationAndPublish(
                eq(doc.getId()), contains("circuit breaker"), eq(-1),
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID));
        verify(pdfStoragePort, never()).delete(anyString());
    }

    // -------------------------------------------------------------------------
    // OptimisticLockingFailureException handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("handleProcessCommand: OptimisticLockingFailureException → publishGenerationFailure with concurrent-modification message")
    void handleProcessCommand_optimisticLockingFailure_publishesFailure() {
        when(pdfDocumentService.findByInvoiceId(DOC_ID))
                .thenThrow(new OptimisticLockingFailureException("version conflict"));

        sagaCommandHandler.handle(DOC_ID, DOC_NUMBER, SIGNED_XML_URL, SAGA_ID, SAGA_STEP, CORR_ID);

        verify(pdfDocumentService).publishGenerationFailure(
                eq(SAGA_ID), eq(SAGA_STEP), eq(CORR_ID), contains("Concurrent modification"));
    }
}
