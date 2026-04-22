package com.wpanther.invoice.pdf;

import com.wpanther.invoice.pdf.infrastructure.adapter.out.pdf.PdfA3Converter;
import com.wpanther.invoice.pdf.infrastructure.adapter.out.pdf.ThaiAmountWordsConverter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.fop.apps.FopFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PDF/A-3 Preview Generator")
class PdfPreviewTest {

    private static final Path OUTPUT_DIR = Path.of("target/preview");
    private static final BigDecimal GRAND_TOTAL = new BigDecimal("5350.00");
    private static final String DOC_NUMBER = "INV-2568-0018";

    private static String signedXml;
    private static SimpleMeterRegistry registry;

    @BeforeAll
    static void loadFixtures() throws Exception {
        ClassPathResource xmlResource = new ClassPathResource("xml/preview-invoice.xml");
        try (InputStream is = xmlResource.getInputStream()) {
            signedXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        Files.createDirectories(OUTPUT_DIR);
        registry = new SimpleMeterRegistry();
    }

    @Test
    @DisplayName("Full pipeline: signed XML → FOP with THSarabunNew → PDF/A-3 with embedded XML")
    void generatePdfA3Preview() throws Exception {
        String amountInWords = ThaiAmountWordsConverter.toWords(GRAND_TOTAL);
        assertThat(amountInWords).isEqualTo("ห้าพันสามร้อยห้าสิบบาทถ้วน");

        // Step 1: Render base PDF using production FOP config (PDF/A-3b + Thai fonts)
        byte[] basePdf = renderBasePdf(amountInWords);
        assertThat(basePdf).isNotEmpty();
        assertThat(basePdf.length).isGreaterThan(50_000); // fonts embedded → large PDF
        assertStartsWithPdfHeader(basePdf);

        // Step 2: Convert to PDF/A-3 with embedded signed XML
        PdfA3Converter converter = new PdfA3Converter("icc/sRGB.icc", registry);
        String xmlFilename = "invoice-" + DOC_NUMBER + ".xml";
        byte[] pdfA3 = converter.convertToPdfA3(basePdf, signedXml, xmlFilename, DOC_NUMBER);

        assertThat(pdfA3).isNotEmpty();
        assertThat(pdfA3.length).isGreaterThan(basePdf.length); // XML attachment adds size
        assertStartsWithPdfHeader(pdfA3);

        // Save for visual inspection
        Path outputPath = OUTPUT_DIR.resolve("invoice-pdfa3-preview.pdf");
        Files.write(outputPath, pdfA3);
        System.out.println("PDF/A-3 saved: " + outputPath.toAbsolutePath() + " (" + pdfA3.length + " bytes)");
    }

    @Test
    @DisplayName("Base FOP PDF with Thai fonts is significantly larger than fallback-only PDF")
    void basePdf_withEmbeddedFonts_isLargerThanFallback() throws Exception {
        String amountInWords = ThaiAmountWordsConverter.toWords(GRAND_TOTAL);

        // Production FOP (Thai fonts embedded) → large PDF
        byte[] productionPdf = renderBasePdf(amountInWords);
        assertThat(productionPdf.length).isGreaterThan(50_000);

        // Test FOP (auto-detect only, no font embedding) → small PDF
        byte[] testPdf = renderTestFopPdf(amountInWords);
        assertThat(testPdf.length).isLessThan(20_000);

        // Production must be significantly larger (font subsets embedded)
        assertThat(productionPdf.length)
                .as("Production PDF with embedded Thai fonts should be much larger than test fallback")
                .isGreaterThan(testPdf.length * 3);

        Path prodPath = OUTPUT_DIR.resolve("invoice-base-preview.pdf");
        Files.write(prodPath, productionPdf);
        System.out.println("Production PDF: " + productionPdf.length + " bytes | Test fallback PDF: " + testPdf.length + " bytes");
    }

    private byte[] renderBasePdf(String amountInWords) throws Exception {
        FopFactory fopFactory = createProductionFopFactory();
        Templates templates = compileTemplates();

        try (ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream()) {
            var fop = fopFactory.newFop(org.apache.fop.apps.MimeConstants.MIME_PDF, pdfOutput);
            Transformer transformer = templates.newTransformer();
            transformer.setParameter("amountInWords", amountInWords);

            Source xmlSource = new StreamSource(
                    new ByteArrayInputStream(signedXml.getBytes(StandardCharsets.UTF_8)));
            Result result = new SAXResult(fop.getDefaultHandler());
            transformer.transform(xmlSource, result);

            return pdfOutput.toByteArray();
        }
    }

    private FopFactory createProductionFopFactory() throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath();
        Path prodConfig = projectRoot.resolve("src/main/resources/fop/fop.xconf");
        try (InputStream is = Files.newInputStream(prodConfig)) {
            return FopFactory.newInstance(prodConfig.getParent().toUri(), is);
        }
    }

    private Templates compileTemplates() throws Exception {
        ClassPathResource xslResource = new ClassPathResource("xsl/invoice.xsl");
        try (InputStream is = xslResource.getInputStream()) {
            return TransformerFactory.newInstance().newTemplates(new StreamSource(is));
        }
    }

    private byte[] renderTestFopPdf(String amountInWords) throws Exception {
        ClassPathResource testConfig = new ClassPathResource("fop/fop.xconf");
        ClassPathResource classpathRoot = new ClassPathResource("");
        FopFactory testFopFactory;
        try (InputStream is = testConfig.getInputStream()) {
            testFopFactory = FopFactory.newInstance(classpathRoot.getURL().toURI(), is);
        }
        Templates templates = compileTemplates();

        try (ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream()) {
            var fop = testFopFactory.newFop(org.apache.fop.apps.MimeConstants.MIME_PDF, pdfOutput);
            Transformer transformer = templates.newTransformer();
            transformer.setParameter("amountInWords", amountInWords);
            Source xmlSource = new StreamSource(
                    new ByteArrayInputStream(signedXml.getBytes(StandardCharsets.UTF_8)));
            transformer.transform(xmlSource, new SAXResult(fop.getDefaultHandler()));
            return pdfOutput.toByteArray();
        }
    }

    private static void assertStartsWithPdfHeader(byte[] pdfBytes) {
        assertThat(new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
    }
}
