package com.wpanther.invoice.pdf.infrastructure.adapter.out.pdf;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FopInvoicePdfGenerator Unit Tests")
class FopInvoicePdfGeneratorTest {

    @Test
    @DisplayName("Constructor succeeds and compiles XSL template")
    void constructor_compilesTemplateSuccessfully() {
        // No exception = template found and compiled
        assertThatCode(() -> new FopInvoicePdfGenerator(2, 52428800L, new SimpleMeterRegistry()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Constructor rejects maxConcurrentRenders < 1 with IllegalStateException")
    void constructor_invalidMaxConcurrentRenders_throwsIllegalStateException() {
        assertThatThrownBy(() -> new FopInvoicePdfGenerator(0, 52428800L, new SimpleMeterRegistry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-concurrent-renders")
                .hasMessageContaining("0");
    }

    @Test
    @DisplayName("Semaphore is initialised with the configured permit count")
    void constructor_semaphorePermitsMatchConfiguration() throws Exception {
        FopInvoicePdfGenerator gen = new FopInvoicePdfGenerator(5, 52428800L, new SimpleMeterRegistry());
        Field f = FopInvoicePdfGenerator.class.getDeclaredField("renderSemaphore");
        f.setAccessible(true);
        Semaphore s = (Semaphore) f.get(gen);
        assertThat(s.availablePermits()).isEqualTo(5);
        assertThat(s.isFair()).isTrue();
    }

    @Test
    @DisplayName("Valid invoice XML → returns non-empty PDF bytes starting with %PDF")
    void generatePdf_validXml_returnsPdfBytes() throws Exception {
        FopInvoicePdfGenerator gen = new FopInvoicePdfGenerator(1, 52428800L, new SimpleMeterRegistry());
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rsm:Invoice_CrossIndustryInvoice"
                + " xmlns:rsm=\"urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2\""
                + " xmlns:ram=\"urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2\">"
                + "<rsm:ExchangedDocument><ram:ID>INV-TEST-001</ram:ID>"
                + "<ram:IssueDateTime>2024-01-15T10:30:00</ram:IssueDateTime>"
                + "<ram:TypeCode>380</ram:TypeCode></rsm:ExchangedDocument>"
                + "<rsm:SupplyChainTradeTransaction>"
                + "<ram:ApplicableHeaderTradeAgreement>"
                + "<ram:SellerTradeParty><ram:Name>Test Seller</ram:Name>"
                + "<ram:SpecifiedTaxRegistration><ram:ID>1234567890123</ram:ID></ram:SpecifiedTaxRegistration>"
                + "<ram:PostalTradeAddress><ram:PostcodeCode>10310</ram:PostcodeCode>"
                + "<ram:CityName>1017</ram:CityName><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>"
                + "</ram:SellerTradeParty>"
                + "<ram:BuyerTradeParty><ram:Name>Test Buyer</ram:Name>"
                + "<ram:SpecifiedTaxRegistration><ram:ID>9876543210987</ram:ID></ram:SpecifiedTaxRegistration>"
                + "<ram:PostalTradeAddress><ram:PostcodeCode>10330</ram:PostcodeCode>"
                + "<ram:CityName>1005</ram:CityName><ram:CountryID>TH</ram:CountryID></ram:PostalTradeAddress>"
                + "</ram:BuyerTradeParty>"
                + "</ram:ApplicableHeaderTradeAgreement>"
                + "<ram:ApplicableHeaderTradeSettlement>"
                + "<ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>"
                + "<ram:ApplicableTradeTax><ram:CalculatedRate>7</ram:CalculatedRate>"
                + "<ram:BasisAmount>1000.00</ram:BasisAmount>"
                + "<ram:CalculatedAmount>70.00</ram:CalculatedAmount></ram:ApplicableTradeTax>"
                + "<ram:SpecifiedTradeSettlementHeaderMonetarySummation>"
                + "<ram:LineTotalAmount>1000.00</ram:LineTotalAmount>"
                + "<ram:GrandTotalAmount>1070.00</ram:GrandTotalAmount>"
                + "</ram:SpecifiedTradeSettlementHeaderMonetarySummation>"
                + "</ram:ApplicableHeaderTradeSettlement>"
                + "<rsm:IncludedSupplyChainTradeLineItem>"
                + "<ram:SpecifiedTradeProduct><ram:Name>Widget</ram:Name></ram:SpecifiedTradeProduct>"
                + "<ram:SpecifiedLineTradeAgreement>"
                + "<ram:NetPriceProductTradePrice><ram:ChargeAmount>100.00</ram:ChargeAmount></ram:NetPriceProductTradePrice>"
                + "</ram:SpecifiedLineTradeAgreement>"
                + "<ram:SpecifiedLineTradeDelivery><ram:BilledQuantity unitCode=\"EA\">10</ram:BilledQuantity></ram:SpecifiedLineTradeDelivery>"
                + "<ram:SpecifiedLineTradeSettlement>"
                + "<ram:SpecifiedTradeSettlementLineMonetarySummation>"
                + "<ram:NetLineTotalAmount>1000.00</ram:NetLineTotalAmount>"
                + "</ram:SpecifiedTradeSettlementLineMonetarySummation>"
                + "</ram:SpecifiedLineTradeSettlement>"
                + "</rsm:IncludedSupplyChainTradeLineItem>"
                + "</rsm:SupplyChainTradeTransaction>"
                + "</rsm:Invoice_CrossIndustryInvoice>";

        byte[] result = gen.generatePdf(xml);

        assertThat(result).isNotEmpty();
        // All PDF files start with the %PDF header
        assertThat(new String(result, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Malformed XML → PdfGenerationException")
    void generatePdf_malformedXml_throwsPdfGenerationException() {
        FopInvoicePdfGenerator gen = new FopInvoicePdfGenerator(1, 52428800L, new SimpleMeterRegistry());
        assertThatThrownBy(() -> gen.generatePdf("this is not xml <<<"))
                .isInstanceOf(FopInvoicePdfGenerator.PdfGenerationException.class);
    }

    @Test
    @DisplayName("Constructor rejects maxPdfSizeBytes < 1 with IllegalStateException")
    void constructor_invalidMaxPdfSizeBytes_throwsIllegalStateException() {
        assertThatThrownBy(() -> new FopInvoicePdfGenerator(1, 0L, new SimpleMeterRegistry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-pdf-size-bytes")
                .hasMessageContaining("0");
    }

    @Test
    @DisplayName("generatePdf() throws PdfGenerationException when PDF exceeds max size")
    void generatePdf_pdfExceedsMaxSize_throwsPdfGenerationException() throws Exception {
        // Set a 1-byte limit so any real PDF will exceed it
        FopInvoicePdfGenerator gen = new FopInvoicePdfGenerator(1, 1L, new SimpleMeterRegistry());
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rsm:Invoice_CrossIndustryInvoice"
                + " xmlns:rsm=\"urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2\""
                + " xmlns:ram=\"urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2\">"
                + "<rsm:ExchangedDocument><ram:ID>INV-TOOBIG</ram:ID></rsm:ExchangedDocument>"
                + "<rsm:SupplyChainTradeTransaction>"
                + "<ram:ApplicableHeaderTradeAgreement>"
                + "<ram:SellerTradeParty><ram:Name>S</ram:Name></ram:SellerTradeParty>"
                + "<ram:BuyerTradeParty><ram:Name>B</ram:Name></ram:BuyerTradeParty>"
                + "</ram:ApplicableHeaderTradeAgreement>"
                + "<ram:ApplicableHeaderTradeSettlement/>"
                + "</rsm:SupplyChainTradeTransaction>"
                + "</rsm:Invoice_CrossIndustryInvoice>";

        assertThatThrownBy(() -> gen.generatePdf(xml))
                .isInstanceOf(FopInvoicePdfGenerator.PdfGenerationException.class)
                .hasMessageContaining("exceeds max allowed size");
    }

    @Test
    @DisplayName("checkFontAvailability() does not throw regardless of font presence")
    void checkFontAvailability_doesNotThrow() {
        FopInvoicePdfGenerator gen = new FopInvoicePdfGenerator(1, 52428800L, new SimpleMeterRegistry());
        // Method logs info (fonts present) or warn (fonts absent) — never throws.
        assertThatCode(() -> gen.checkFontAvailability()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PdfGenerationException(String) 1-arg constructor carries the message")
    void pdfGenerationException_messageOnlyConstructor_hasMessage() {
        var ex = new FopInvoicePdfGenerator.PdfGenerationException("FOP failed");
        assertThat(ex.getMessage()).isEqualTo("FOP failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("generatePdf() on an interrupted thread throws PdfGenerationException")
    void generatePdf_threadAlreadyInterrupted_throwsPdfGenerationException() {
        FopInvoicePdfGenerator gen = new FopInvoicePdfGenerator(1, 52428800L, new SimpleMeterRegistry());
        Thread.currentThread().interrupt();  // mark thread as interrupted before acquire()
        try {
            assertThatThrownBy(() -> gen.generatePdf("<invoice/>"))
                    .isInstanceOf(FopInvoicePdfGenerator.PdfGenerationException.class)
                    .hasMessageContaining("interrupted");
        } finally {
            Thread.interrupted();  // restore clean interrupted status for subsequent tests
        }
    }

    @Test
    @DisplayName("Semaphore blocks callers when all permits are held")
    void generatePdf_semaphoreBlocksWhenAtCapacity() throws Exception {
        FopInvoicePdfGenerator gen = new FopInvoicePdfGenerator(1, 52428800L, new SimpleMeterRegistry());

        // Drain the single permit so the next caller must wait
        Field f = FopInvoicePdfGenerator.class.getDeclaredField("renderSemaphore");
        f.setAccessible(true);
        Semaphore sem = (Semaphore) f.get(gen);
        sem.acquire();

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = exec.submit(() -> {
                try {
                    gen.generatePdf("<invoice/>");
                } catch (FopInvoicePdfGenerator.PdfGenerationException ignored) {
                    // expected once permit is released — not what we are testing here
                }
            });

            // While the permit is held, the task must not complete
            assertThatThrownBy(() -> future.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            // Release permit → task unblocks and finishes (may fail on bad XML, that is fine)
            sem.release();
            future.get(5, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
    }
}
