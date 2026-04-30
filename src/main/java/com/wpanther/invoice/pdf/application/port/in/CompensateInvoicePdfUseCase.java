package com.wpanther.invoice.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port for invoice PDF compensation.
 * Called by SagaCommandHandler with plain fields — no command objects.
 */
public interface CompensateInvoicePdfUseCase {

    void handle(String documentId, String sagaId, SagaStep sagaStep, String correlationId);
}
