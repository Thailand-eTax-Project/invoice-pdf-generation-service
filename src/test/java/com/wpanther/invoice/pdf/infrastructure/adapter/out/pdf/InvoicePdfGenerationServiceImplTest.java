package com.wpanther.invoice.pdf.infrastructure.adapter.out.pdf;

import com.wpanther.invoice.pdf.domain.exception.InvoicePdfGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoicePdfGenerationServiceImpl Unit Tests")
class InvoicePdfGenerationServiceImplTest {

    @Mock private FopInvoicePdfGenerator fopGenerator;
    @Mock private PdfA3Converter pdfA3Converter;

    private InvoicePdfGenerationServiceImpl service;

    private static final String INVOICE_NUMBER = "INV-2024-001";
    private static final String SIGNED_XML = "<rsm:Invoice_CrossIndustryInvoice xmlns:rsm=\"urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2\" xmlns:ram=\"urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2\"><rsm:ExchangedDocument><ram:ID>INV-2024-001</ram:ID></rsm:ExchangedDocument></rsm:Invoice_CrossIndustryInvoice>";
    private static final byte[] BASE_PDF = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] PDFA3_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x41};

    @BeforeEach
    void setUp() {
        service = new InvoicePdfGenerationServiceImpl(fopGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("Happy path: signed XML -> FOP -> PDFBox -> PDF/A-3 bytes returned")
    void generatePdf_happyPath_returnsPdfA3Bytes() throws Exception {
        when(fopGenerator.generatePdf(SIGNED_XML)).thenReturn(BASE_PDF);
        when(pdfA3Converter.convertToPdfA3(eq(BASE_PDF), eq(SIGNED_XML), anyString(), eq(INVOICE_NUMBER)))
                .thenReturn(PDFA3_BYTES);

        byte[] result = service.generatePdf(INVOICE_NUMBER, SIGNED_XML);

        assertThat(result).isEqualTo(PDFA3_BYTES);
        verify(fopGenerator).generatePdf(SIGNED_XML);
        verify(pdfA3Converter).convertToPdfA3(
                eq(BASE_PDF), eq(SIGNED_XML),
                eq("invoice-" + INVOICE_NUMBER + ".xml"),
                eq(INVOICE_NUMBER));
    }

    @Test
    @DisplayName("Null xmlContent -> InvoicePdfGenerationException before FOP")
    void generatePdf_nullXmlContent_throwsException() {
        assertThatThrownBy(() -> service.generatePdf(INVOICE_NUMBER, null))
                .isInstanceOf(InvoicePdfGenerationException.class)
                .hasMessageContaining("xmlContent");
        verifyNoInteractions(fopGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("Blank xmlContent -> InvoicePdfGenerationException before FOP")
    void generatePdf_blankXmlContent_throwsException() {
        assertThatThrownBy(() -> service.generatePdf(INVOICE_NUMBER, "   "))
                .isInstanceOf(InvoicePdfGenerationException.class)
                .hasMessageContaining("xmlContent");
        verifyNoInteractions(fopGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("FOP throws PdfGenerationException -> wrapped in InvoicePdfGenerationException")
    void generatePdf_fopThrows_wrappedAsInvoicePdfGenerationException() throws Exception {
        when(fopGenerator.generatePdf(anyString()))
                .thenThrow(new FopInvoicePdfGenerator.PdfGenerationException("FOP failed"));

        assertThatThrownBy(() -> service.generatePdf(INVOICE_NUMBER, SIGNED_XML))
                .isInstanceOf(InvoicePdfGenerationException.class)
                .hasMessageContaining("PDF generation failed")
                .hasMessageContaining("FOP failed");
        verify(pdfA3Converter, never()).convertToPdfA3(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PDFBox throws PdfConversionException -> wrapped in InvoicePdfGenerationException")
    void generatePdf_pdfboxThrows_wrappedAsInvoicePdfGenerationException() throws Exception {
        when(fopGenerator.generatePdf(anyString())).thenReturn(BASE_PDF);
        when(pdfA3Converter.convertToPdfA3(any(), any(), any(), any()))
                .thenThrow(new PdfA3Converter.PdfConversionException("PDFBox failed"));

        assertThatThrownBy(() -> service.generatePdf(INVOICE_NUMBER, SIGNED_XML))
                .isInstanceOf(InvoicePdfGenerationException.class)
                .hasMessageContaining("PDF/A-3 conversion failed")
                .hasMessageContaining("PDFBox failed");
    }
}
