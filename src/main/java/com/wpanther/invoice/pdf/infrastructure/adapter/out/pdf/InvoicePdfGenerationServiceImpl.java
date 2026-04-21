package com.wpanther.invoice.pdf.infrastructure.adapter.out.pdf;

import com.wpanther.invoice.pdf.domain.exception.InvoicePdfGenerationException;
import com.wpanther.invoice.pdf.domain.service.InvoicePdfGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InvoicePdfGenerationServiceImpl implements InvoicePdfGenerationService {

    private final FopInvoicePdfGenerator fopPdfGenerator;
    private final PdfA3Converter pdfA3Converter;

    public InvoicePdfGenerationServiceImpl(FopInvoicePdfGenerator fopPdfGenerator,
                                           PdfA3Converter pdfA3Converter) {
        this.fopPdfGenerator = fopPdfGenerator;
        this.pdfA3Converter = pdfA3Converter;
    }

    @Override
    public byte[] generatePdf(String invoiceNumber, String xmlContent)
            throws InvoicePdfGenerationException {

        log.info("Starting PDF generation for invoice: {}", invoiceNumber);

        if (xmlContent == null || xmlContent.isBlank()) {
            throw new InvoicePdfGenerationException(
                    "xmlContent (signed XML) is null or blank for invoice: " + invoiceNumber);
        }

        try {
            // Generate base PDF using FOP directly from signed XML
            byte[] basePdf = fopPdfGenerator.generatePdf(xmlContent);
            log.debug("Generated base PDF: {} bytes", basePdf.length);

            // Convert to PDF/A-3 and embed original XML
            String xmlFilename = "invoice-" + invoiceNumber + ".xml";
            byte[] pdfA3 = pdfA3Converter.convertToPdfA3(basePdf, xmlContent, xmlFilename, invoiceNumber);
            log.info("Generated PDF/A-3 for invoice {}: {} bytes", invoiceNumber, pdfA3.length);

            return pdfA3;

        } catch (FopInvoicePdfGenerator.PdfGenerationException e) {
            log.error("FOP PDF generation failed for invoice: {}", invoiceNumber, e);
            throw new InvoicePdfGenerationException("PDF generation failed: " + e.getMessage(), e);
        } catch (PdfA3Converter.PdfConversionException e) {
            log.error("PDF/A-3 conversion failed for invoice: {}", invoiceNumber, e);
            throw new InvoicePdfGenerationException("PDF/A-3 conversion failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during PDF generation for invoice: {}", invoiceNumber, e);
            throw new InvoicePdfGenerationException("PDF generation failed: " + e.getMessage(), e);
        }
    }
}
