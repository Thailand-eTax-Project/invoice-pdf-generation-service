package com.wpanther.invoice.pdf.application.service;

import com.wpanther.invoice.pdf.application.dto.event.InvoicePdfGeneratedEvent;
import com.wpanther.invoice.pdf.application.port.out.PdfEventPort;
import com.wpanther.invoice.pdf.application.port.out.SagaReplyPort;
import com.wpanther.invoice.pdf.domain.model.InvoicePdfDocument;
import com.wpanther.invoice.pdf.domain.repository.InvoicePdfDocumentRepository;
import com.wpanther.invoice.pdf.infrastructure.metrics.PdfGenerationMetrics;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class InvoicePdfDocumentService {

    private final InvoicePdfDocumentRepository repository;
    private final PdfEventPort pdfEventPort;
    private final SagaReplyPort sagaReplyPort;

    @Autowired(required = false)
    private PdfGenerationMetrics pdfGenerationMetrics;

    public InvoicePdfDocumentService(InvoicePdfDocumentRepository repository,
                                     PdfEventPort pdfEventPort,
                                     SagaReplyPort sagaReplyPort,
                                     PdfGenerationMetrics pdfGenerationMetrics) {
        this.repository = repository;
        this.pdfEventPort = pdfEventPort;
        this.sagaReplyPort = sagaReplyPort;
        this.pdfGenerationMetrics = pdfGenerationMetrics;
    }

    @Transactional(readOnly = true)
    public Optional<InvoicePdfDocument> findByInvoiceId(String invoiceId) {
        return repository.findByInvoiceId(invoiceId);
    }

    @Transactional
    public InvoicePdfDocument beginGeneration(String invoiceId, String invoiceNumber) {
        log.info("Initiating PDF generation for invoice: {}", invoiceNumber);
        InvoicePdfDocument document = InvoicePdfDocument.builder()
                .invoiceId(invoiceId)
                .invoiceNumber(invoiceNumber)
                .build();
        document.startGeneration();
        return repository.save(document);
    }

    @Transactional
    public InvoicePdfDocument replaceAndBeginGeneration(
            UUID existingId, int previousRetryCount, String invoiceId, String invoiceNumber) {
        log.info("Replacing PDF document {} and initiating new generation for invoice: {}", existingId, invoiceNumber);
        repository.deleteById(existingId);
        repository.flush();
        InvoicePdfDocument document = InvoicePdfDocument.builder()
                .invoiceId(invoiceId)
                .invoiceNumber(invoiceNumber)
                .build();
        document.startGeneration();
        document.incrementRetryCountTo(previousRetryCount + 1);
        return repository.save(document);
    }

    @Transactional
    public void completeGenerationAndPublish(UUID documentId, String s3Key, String fileUrl,
                                             long fileSize, int previousRetryCount,
                                             String cmdDocumentId, String cmdDocumentNumber,
                                             String sagaId, SagaStep sagaStep, String correlationId) {
        InvoicePdfDocument doc = requireDocument(documentId);
        doc.markCompleted(s3Key, fileUrl, fileSize, LocalDateTime.now());
        doc.markXmlEmbedded();
        applyRetryCount(doc, previousRetryCount);
        doc = repository.save(doc);

        pdfEventPort.publishPdfGenerated(buildGeneratedEvent(doc, cmdDocumentId, cmdDocumentNumber, sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId, doc.getDocumentUrl(), doc.getFileSize());

        log.info("Completed PDF generation and published events for saga {} invoice {}", sagaId, doc.getInvoiceNumber());
    }

    @Transactional
    public void failGenerationAndPublish(UUID documentId, String errorMessage,
                                         int previousRetryCount,
                                         String sagaId, SagaStep sagaStep, String correlationId) {
        String safeError = errorMessage != null ? errorMessage : "PDF generation failed";
        InvoicePdfDocument doc = requireDocument(documentId);
        doc.markFailed(safeError, LocalDateTime.now());
        applyRetryCount(doc, previousRetryCount);
        repository.save(doc);

        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, safeError);

        log.warn("PDF generation failed for saga {} invoice {}: {}", sagaId, doc.getInvoiceNumber(), safeError);
    }

    @Transactional
    public void deleteById(UUID documentId) {
        repository.deleteById(documentId);
        repository.flush();
    }

    @Transactional
    public void publishIdempotentSuccess(InvoicePdfDocument existing,
                                         String documentId, String documentNumber,
                                         String sagaId, SagaStep sagaStep, String correlationId) {
        pdfEventPort.publishPdfGenerated(buildGeneratedEvent(existing, documentId, documentNumber, sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId, existing.getDocumentUrl(), existing.getFileSize());
        log.warn("PDF already generated for saga {} — re-publishing SUCCESS reply", sagaId);
    }

    @Transactional
    public void publishRetryExhausted(String sagaId, SagaStep sagaStep, String correlationId,
                                      String documentId, String documentNumber) {
        if (pdfGenerationMetrics != null) {
            pdfGenerationMetrics.recordRetryExhausted(sagaId, documentId, documentNumber);
        }
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, "Maximum retry attempts exceeded");
        log.error("Max retries exceeded for saga {} document {}", sagaId, documentNumber);
    }

    @Transactional
    public void publishGenerationFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, errorMessage);
    }

    @Transactional
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
    }

    @Transactional
    public void publishCompensationFailure(String sagaId, SagaStep sagaStep, String correlationId, String error) {
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, error);
    }

    private InvoicePdfDocument requireDocument(UUID documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> {
                    log.error("InvoicePdfDocument not found for id={} — TX2 may have raced with compensation", documentId);
                    return new IllegalStateException("Expected invoice PDF document is absent — internal state error");
                });
    }

    private void applyRetryCount(InvoicePdfDocument doc, int previousRetryCount) {
        if (previousRetryCount < 0) return;
        doc.incrementRetryCountTo(previousRetryCount + 1);
    }

    private InvoicePdfGeneratedEvent buildGeneratedEvent(InvoicePdfDocument doc,
                                                          String documentId, String documentNumber,
                                                          String sagaId, String correlationId) {
        return new InvoicePdfGeneratedEvent(
                sagaId,
                documentId,
                doc.getInvoiceNumber(),
                doc.getDocumentUrl(),
                doc.getFileSize(),
                doc.isXmlEmbedded(),
                correlationId);
    }
}
