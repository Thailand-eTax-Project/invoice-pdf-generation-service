package com.wpanther.invoice.pdf.domain.service;

import com.wpanther.invoice.pdf.domain.exception.InvoicePdfGenerationException;

public interface InvoicePdfGenerationService {

    /**
     * Generate PDF from signed XML.
     *
     * @param invoiceNumber Invoice number (for naming and logging)
     * @param xmlContent Signed CrossIndustryInvoice XML (used for both rendering and embedding)
     * @return PDF/A-3 bytes
     * @throws InvoicePdfGenerationException if generation fails
     */
    byte[] generatePdf(String invoiceNumber, String xmlContent)
        throws InvoicePdfGenerationException;
}
