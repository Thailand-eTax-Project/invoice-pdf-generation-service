package com.wpanther.invoice.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port for invoice PDF generation.
 * Called by SagaCommandHandler with plain fields — no command objects.
 */
public interface ProcessInvoicePdfUseCase {

    void handle(String documentId, String documentNumber, String signedXmlUrl,
                String sagaId, SagaStep sagaStep, String correlationId);
}
