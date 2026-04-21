# Generate PDF from Signed XML Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace invoiceDataJson-based PDF generation with direct CII XML-to-PDF rendering, removing the JSON→XML conversion layer.

**Architecture:** The signed CrossIndustryInvoice XML (downloaded from `signedXmlUrl`) becomes the single source of truth for PDF rendering. A new XSL-FO template matches CII elements directly via namespace-aware XPaths. The `invoiceDataJson` field is removed from all command DTOs and the service interface.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache FOP 2.9, Apache PDFBox 3.0.1, XSL-FO 1.0

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `src/main/resources/xsl/invoice.xsl` | CII namespace-aware XSL-FO template (replaces flat-XML template) |
| Modify | `src/main/java/.../domain/event/ProcessInvoicePdfCommand.java` | Remove `invoiceDataJson` field |
| Modify | `src/main/java/.../domain/service/InvoicePdfGenerationService.java` | Simplify to 2-arg `generatePdf(invoiceNumber, xmlContent)` |
| Modify | `src/main/java/.../infrastructure/adapter/out/pdf/InvoicePdfGenerationServiceImpl.java` | Remove JSON→XML conversion, pass signed XML directly to FOP |
| Modify | `src/main/java/.../application/service/SagaCommandHandler.java` | Remove `invoiceDataJson` references |
| Modify | `src/main/java/.../infrastructure/adapter/in/kafka/KafkaProcessInvoicePdfCommand.java` | Remove `invoiceDataJson` field |
| Modify | `src/main/java/.../infrastructure/adapter/in/kafka/KafkaCommandMapper.java` | Remove `invoiceDataJson` from mapping |
| Modify | `src/test/java/.../infrastructure/adapter/out/pdf/InvoicePdfGenerationServiceImplTest.java` | Rewrite for 2-arg `generatePdf` |
| Modify | `src/test/java/.../infrastructure/adapter/out/pdf/FopInvoicePdfGeneratorTest.java` | Update XML test data to CII format |
| Modify | `src/test/java/.../application/service/SagaCommandHandlerTest.java` | Update for 2-arg `generatePdf` and no `invoiceDataJson` |

---

### Task 1: Rewrite XSL-FO template for CII XML

**Files:**
- Modify: `src/main/resources/xsl/invoice.xsl`

This is the core change — the template must match `rsm:Invoice_CrossIndustryInvoice` with namespace-aware XPaths. The visual layout (page size, fonts, colors, header/footer, sections) stays identical to the current template.

- [ ] **Step 1: Replace `invoice.xsl` with CII-aware template**

Write the new template. The root match changes from `/invoice` to `/rsm:Invoice_CrossIndustryInvoice`. All XPath expressions use `rsm:` and `ram:` prefixed names. Sections preserved: header, footer, invoice-title, parties-info, invoice-details, line-items, totals.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
    xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2">

    <xsl:output method="xml" indent="yes"/>

    <xsl:variable name="page-width">210mm</xsl:variable>
    <xsl:variable name="page-height">297mm</xsl:variable>
    <xsl:variable name="margin">15mm</xsl:variable>

    <xsl:variable name="font-family">NotoSansThaiLooped, Helvetica, sans-serif</xsl:variable>
    <xsl:variable name="font-size">11pt</xsl:variable>
    <xsl:variable name="font-size-small">9pt</xsl:variable>
    <xsl:variable name="font-size-large">14pt</xsl:variable>
    <xsl:variable name="font-size-title">18pt</xsl:variable>

    <!-- Convenience variables for deep paths -->
    <xsl:variable name="headerAgreement"
        select="rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement"/>
    <xsl:variable name="headerSettlement"
        select="rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement"/>
    <xsl:variable name="monetarySummation"
        select="$headerSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation"/>

    <xsl:template match="/rsm:Invoice_CrossIndustryInvoice">
        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="invoice-page"
                    page-width="{$page-width}" page-height="{$page-height}"
                    margin-top="{$margin}" margin-bottom="{$margin}"
                    margin-left="{$margin}" margin-right="{$margin}">
                    <fo:region-body margin-top="20mm" margin-bottom="20mm"/>
                    <fo:region-before extent="20mm"/>
                    <fo:region-after extent="20mm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <fo:page-sequence master-reference="invoice-page">
                <fo:static-content flow-name="xsl-region-before">
                    <xsl:call-template name="header"/>
                </fo:static-content>
                <fo:static-content flow-name="xsl-region-after">
                    <xsl:call-template name="footer"/>
                </fo:static-content>
                <fo:flow flow-name="xsl-region-body">
                    <xsl:call-template name="invoice-title"/>
                    <xsl:call-template name="parties-info"/>
                    <xsl:call-template name="invoice-details"/>
                    <xsl:call-template name="line-items"/>
                    <xsl:call-template name="totals"/>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

    <!-- Header -->
    <xsl:template name="header">
        <fo:block font-family="{$font-family}" font-size="{$font-size-small}" color="#666666"
            border-bottom="0.5pt solid #cccccc" padding-bottom="2mm">
            <fo:table width="100%" table-layout="fixed">
                <fo:table-column column-width="50%"/>
                <fo:table-column column-width="50%"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell>
                            <fo:block text-align="left">
                                <xsl:value-of select="$headerAgreement/ram:SellerTradeParty/ram:Name"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block text-align="right">
                                e-Tax Invoice / ใบแจ้งหนี้อิเล็กทรอนิกส์
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:block>
    </xsl:template>

    <!-- Footer -->
    <xsl:template name="footer">
        <fo:block font-family="{$font-family}" font-size="{$font-size-small}" color="#666666"
            border-top="0.5pt solid #cccccc" padding-top="2mm">
            <fo:table width="100%" table-layout="fixed">
                <fo:table-column column-width="33%"/>
                <fo:table-column column-width="34%"/>
                <fo:table-column column-width="33%"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell>
                            <fo:block text-align="left">
                                เอกสารนี้จัดทำด้วยระบบคอมพิวเตอร์
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block text-align="center">
                                หน้า <fo:page-number/> / <fo:page-number-citation ref-id="last-page"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block text-align="right">
                                <xsl:value-of select="rsm:ExchangedDocument/ram:ID"/>
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:block>
    </xsl:template>

    <!-- Invoice title -->
    <xsl:template name="invoice-title">
        <fo:block font-family="{$font-family}" font-size="{$font-size-title}" font-weight="bold"
            text-align="center" space-after="5mm" color="#333333">
            ใบแจ้งหนี้ / INVOICE
        </fo:block>
        <fo:block font-family="{$font-family}" font-size="{$font-size}" text-align="center"
            space-after="10mm" color="#666666">
            (ต้นฉบับ / Original)
        </fo:block>
    </xsl:template>

    <!-- Parties info -->
    <xsl:template name="parties-info">
        <xsl:variable name="seller" select="$headerAgreement/ram:SellerTradeParty"/>
        <xsl:variable name="buyer" select="$headerAgreement/ram:BuyerTradeParty"/>
        <fo:table width="100%" table-layout="fixed" space-after="8mm">
            <fo:table-column column-width="50%"/>
            <fo:table-column column-width="50%"/>
            <fo:table-body>
                <fo:table-row>
                    <!-- Seller -->
                    <fo:table-cell padding-right="5mm">
                        <fo:block font-family="{$font-family}" font-size="{$font-size}"
                            background-color="#f5f5f5" padding="3mm" border="0.5pt solid #dddddd">
                            <fo:block font-weight="bold" space-after="2mm">ผู้ขาย / Seller</fo:block>
                            <fo:block><xsl:value-of select="$seller/ram:Name"/></fo:block>
                            <fo:block>
                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:BuildingNumber"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:CitySubDivisionName"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:CityName"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:PostcodeCode"/>
                            </fo:block>
                            <fo:block>
                                เลขประจำตัวผู้เสียภาษี: <xsl:value-of select="$seller/ram:SpecifiedTaxRegistration/ram:ID"/>
                            </fo:block>
                            <xsl:if test="$seller/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber">
                                <fo:block>โทร: <xsl:value-of select="$seller/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber"/></fo:block>
                            </xsl:if>
                            <xsl:if test="$seller/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID">
                                <fo:block>อีเมล: <xsl:value-of select="$seller/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID"/></fo:block>
                            </xsl:if>
                        </fo:block>
                    </fo:table-cell>
                    <!-- Buyer -->
                    <fo:table-cell padding-left="5mm">
                        <fo:block font-family="{$font-family}" font-size="{$font-size}"
                            background-color="#f5f5f5" padding="3mm" border="0.5pt solid #dddddd">
                            <fo:block font-weight="bold" space-after="2mm">ผู้ซื้อ / Buyer</fo:block>
                            <fo:block><xsl:value-of select="$buyer/ram:Name"/></fo:block>
                            <fo:block>
                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:BuildingNumber"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:CitySubDivisionName"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:CityName"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:PostcodeCode"/>
                            </fo:block>
                            <fo:block>
                                เลขประจำตัวผู้เสียภาษี: <xsl:value-of select="$buyer/ram:SpecifiedTaxRegistration/ram:ID"/>
                            </fo:block>
                            <xsl:if test="$buyer/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber">
                                <fo:block>โทร: <xsl:value-of select="$buyer/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber"/></fo:block>
                            </xsl:if>
                            <xsl:if test="$buyer/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID">
                                <fo:block>อีเมล: <xsl:value-of select="$buyer/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID"/></fo:block>
                            </xsl:if>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-body>
        </fo:table>
    </xsl:template>

    <!-- Invoice details -->
    <xsl:template name="invoice-details">
        <fo:table width="100%" table-layout="fixed" space-after="8mm"
            font-family="{$font-family}" font-size="{$font-size}">
            <fo:table-column column-width="25%"/>
            <fo:table-column column-width="25%"/>
            <fo:table-column column-width="25%"/>
            <fo:table-column column-width="25%"/>
            <fo:table-body>
                <fo:table-row>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                        <fo:block font-weight="bold">เลขที่เอกสาร</fo:block>
                        <fo:block font-size="{$font-size-small}">Invoice No.</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block><xsl:value-of select="rsm:ExchangedDocument/ram:ID"/></fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                        <fo:block font-weight="bold">วันที่</fo:block>
                        <fo:block font-size="{$font-size-small}">Date</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block><xsl:value-of select="rsm:ExchangedDocument/ram:IssueDateTime"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <fo:table-row>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                        <fo:block font-weight="bold">ประเภท</fo:block>
                        <fo:block font-size="{$font-size-small}">Type</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block><xsl:value-of select="rsm:ExchangedDocument/ram:TypeCode"/></fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                        <fo:block font-weight="bold">สกุลเงิน</fo:block>
                        <fo:block font-size="{$font-size-small}">Currency</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block><xsl:value-of select="$headerSettlement/ram:InvoiceCurrencyCode"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-body>
        </fo:table>
    </xsl:template>

    <!-- Line items -->
    <xsl:template name="line-items">
        <fo:table width="100%" table-layout="fixed" space-after="5mm"
            font-family="{$font-family}" font-size="{$font-size}">
            <fo:table-column column-width="8%"/>
            <fo:table-column column-width="37%"/>
            <fo:table-column column-width="12%"/>
            <fo:table-column column-width="10%"/>
            <fo:table-column column-width="15%"/>
            <fo:table-column column-width="18%"/>

            <fo:table-header>
                <fo:table-row background-color="#4a4a4a" color="white" font-weight="bold">
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="center">ลำดับ</fo:block>
                        <fo:block text-align="center" font-size="{$font-size-small}">No.</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block>รายการ</fo:block>
                        <fo:block font-size="{$font-size-small}">Description</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="right">จำนวน</fo:block>
                        <fo:block text-align="right" font-size="{$font-size-small}">Qty</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="center">หน่วย</fo:block>
                        <fo:block text-align="center" font-size="{$font-size-small}">Unit</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="right">ราคา/หน่วย</fo:block>
                        <fo:block text-align="right" font-size="{$font-size-small}">Unit Price</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="right">จำนวนเงิน</fo:block>
                        <fo:block text-align="right" font-size="{$font-size-small}">Amount</fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-header>

            <fo:table-body>
                <xsl:for-each select="rsm:SupplyChainTradeTransaction/rsm:IncludedSupplyChainTradeLineItem">
                    <fo:table-row>
                        <xsl:attribute name="background-color">
                            <xsl:choose>
                                <xsl:when test="position() mod 2 = 0">#f9f9f9</xsl:when>
                                <xsl:otherwise>white</xsl:otherwise>
                            </xsl:choose>
                        </xsl:attribute>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="center"><xsl:value-of select="position()"/></fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block><xsl:value-of select="ram:SpecifiedTradeProduct/ram:Name"/></fo:block>
                            <xsl:if test="ram:AssociatedDocumentLineDocument/ram:LineID">
                                <fo:block font-size="{$font-size-small}" color="#666666">
                                    รหัส: <xsl:value-of select="ram:AssociatedDocumentLineDocument/ram:LineID"/>
                                </fo:block>
                            </xsl:if>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="right">
                                <xsl:value-of select="format-number(ram:SpecifiedLineTradeDelivery/ram:BilledQuantity, '#,##0.00')"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="center">
                                <xsl:value-of select="ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="right">
                                <xsl:value-of select="format-number(ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount, '#,##0.00')"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="right">
                                <xsl:value-of select="format-number(ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:NetLineTotalAmount, '#,##0.00')"/>
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </xsl:for-each>
            </fo:table-body>
        </fo:table>
    </xsl:template>

    <!-- Totals -->
    <xsl:template name="totals">
        <fo:table width="100%" table-layout="fixed" space-after="8mm"
            font-family="{$font-family}" font-size="{$font-size}">
            <fo:table-column column-width="60%"/>
            <fo:table-column column-width="22%"/>
            <fo:table-column column-width="18%"/>
            <fo:table-body>
                <!-- Subtotal -->
                <fo:table-row>
                    <fo:table-cell><fo:block></fo:block></fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#f5f5f5">
                        <fo:block text-align="right">รวมเงิน / Subtotal</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block text-align="right">
                            <xsl:value-of select="format-number($monetarySummation/ram:LineTotalAmount, '#,##0.00')"/>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>

                <!-- VAT -->
                <fo:table-row>
                    <fo:table-cell><fo:block></fo:block></fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#f5f5f5">
                        <fo:block text-align="right">
                            ภาษีมูลค่าเพิ่ม <xsl:value-of select="$headerSettlement/ram:ApplicableTradeTax/ram:CalculatedRate"/>% / VAT
                        </fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block text-align="right">
                            <xsl:value-of select="format-number($headerSettlement/ram:ApplicableTradeTax/ram:CalculatedAmount, '#,##0.00')"/>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>

                <!-- Grand Total -->
                <fo:table-row font-weight="bold" font-size="{$font-size-large}">
                    <fo:table-cell><fo:block></fo:block></fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333" background-color="#4a4a4a" color="white">
                        <fo:block text-align="right">ยอดรวมทั้งสิ้น / Grand Total</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333" background-color="#f0f0f0">
                        <fo:block text-align="right">
                            <xsl:value-of select="format-number($monetarySummation/ram:GrandTotalAmount, '#,##0.00')"/>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-body>
        </fo:table>

        <!-- End marker for page counting -->
        <fo:block id="last-page"/>
    </xsl:template>

</xsl:stylesheet>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/xsl/invoice.xsl
git commit -m "refactor: rewrite invoice.xsl to match CII CrossIndustryInvoice XML directly"
```

---

### Task 2: Simplify domain service interface

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/pdf/domain/service/InvoicePdfGenerationService.java`

- [ ] **Step 1: Update interface to remove `invoiceDataJson` parameter**

Replace the 3-arg method with a 2-arg method:

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/domain/service/InvoicePdfGenerationService.java
git commit -m "refactor: remove invoiceDataJson from InvoicePdfGenerationService interface"
```

---

### Task 3: Simplify InvoicePdfGenerationServiceImpl

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/pdf/InvoicePdfGenerationServiceImpl.java`

- [ ] **Step 1: Rewrite implementation to pass signed XML directly to FOP**

Remove `ObjectMapper`, `defaultVatRate`, `maxJsonSizeBytes`, `convertJsonToXml()`, `writeElement()`, `getTextValue()`, `validateXmlWellFormedness()`, `XML_OUTPUT_FACTORY`, `SAX_PARSER_FACTORY`. The method body becomes: validate `xmlContent` → FOP → PDF/A-3.

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/pdf/InvoicePdfGenerationServiceImpl.java
git commit -m "refactor: remove JSON-to-XML conversion from InvoicePdfGenerationServiceImpl"
```

---

### Task 4: Remove invoiceDataJson from command DTOs

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/pdf/domain/event/ProcessInvoicePdfCommand.java`
- Modify: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaProcessInvoicePdfCommand.java`
- Modify: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapper.java`

- [ ] **Step 1: Remove `invoiceDataJson` from `ProcessInvoicePdfCommand`**

Remove the field, the `@JsonProperty` annotation, and update both constructors. The full `@JsonCreator` constructor drops the `invoiceDataJson` parameter. The convenience constructor drops it too.

```java
package com.wpanther.invoice.pdf.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public class ProcessInvoicePdfCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("signedXmlUrl")
    private final String signedXmlUrl;

    @JsonCreator
    public ProcessInvoicePdfCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("signedXmlUrl") String signedXmlUrl) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.signedXmlUrl = signedXmlUrl;
    }

    public ProcessInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                     String documentId, String documentNumber,
                                     String signedXmlUrl) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = Objects.requireNonNull(documentId, "documentId is required");
        this.documentNumber = Objects.requireNonNull(documentNumber, "documentNumber is required");
        this.signedXmlUrl = Objects.requireNonNull(signedXmlUrl, "signedXmlUrl is required");
    }
}
```

- [ ] **Step 2: Remove `invoiceDataJson` from `KafkaProcessInvoicePdfCommand`**

Same change — remove the field, `@JsonProperty`, and both constructor parameters:

```java
package com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public class KafkaProcessInvoicePdfCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("signedXmlUrl")
    private final String signedXmlUrl;

    @JsonCreator
    public KafkaProcessInvoicePdfCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("signedXmlUrl") String signedXmlUrl) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.signedXmlUrl = signedXmlUrl;
    }

    public KafkaProcessInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                         String documentId, String documentNumber,
                                         String signedXmlUrl) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = Objects.requireNonNull(documentId, "documentId is required");
        this.documentNumber = Objects.requireNonNull(documentNumber, "documentNumber is required");
        this.signedXmlUrl = Objects.requireNonNull(signedXmlUrl, "signedXmlUrl is required");
    }
}
```

- [ ] **Step 3: Update `KafkaCommandMapper` to drop `invoiceDataJson`**

```java
package com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka;

import com.wpanther.invoice.pdf.domain.event.CompensateInvoicePdfCommand;
import com.wpanther.invoice.pdf.domain.event.ProcessInvoicePdfCommand;
import org.springframework.stereotype.Component;

@Component
public class KafkaCommandMapper {

    public ProcessInvoicePdfCommand toProcess(KafkaProcessInvoicePdfCommand src) {
        return new ProcessInvoicePdfCommand(
                src.getSagaId(), src.getSagaStep(), src.getCorrelationId(),
                src.getDocumentId(), src.getDocumentNumber(),
                src.getSignedXmlUrl());
    }

    public CompensateInvoicePdfCommand toCompensate(KafkaCompensateInvoicePdfCommand src) {
        return new CompensateInvoicePdfCommand(
                src.getSagaId(), src.getSagaStep(), src.getCorrelationId(),
                src.getDocumentId());
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/domain/event/ProcessInvoicePdfCommand.java \
        src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaProcessInvoicePdfCommand.java \
        src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapper.java
git commit -m "refactor: remove invoiceDataJson from saga command DTOs"
```

---

### Task 5: Update SagaCommandHandler

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/pdf/application/service/SagaCommandHandler.java`

- [ ] **Step 1: Remove `invoiceDataJson` references in `handle()`**

In the `handle(ProcessInvoicePdfCommand command)` method, change the `generatePdf` call from 3 args to 2 args:

Change this line (around line 160-161):
```java
byte[] pdfBytes = pdfGenerationService.generatePdf(
        documentNumber, signedXml, command.getInvoiceDataJson());
```
To:
```java
byte[] pdfBytes = pdfGenerationService.generatePdf(documentNumber, signedXml);
```

No other changes needed in `SagaCommandHandler` — the `command.getInvoiceDataJson()` call appears only once.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/application/service/SagaCommandHandler.java
git commit -m "refactor: remove invoiceDataJson from SagaCommandHandler"
```

---

### Task 6: Update tests

**Files:**
- Modify: `src/test/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/pdf/InvoicePdfGenerationServiceImplTest.java`
- Modify: `src/test/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/pdf/FopInvoicePdfGeneratorTest.java`
- Modify: `src/test/java/com/wpanther/invoice/pdf/application/service/SagaCommandHandlerTest.java`

- [ ] **Step 1: Rewrite `InvoicePdfGenerationServiceImplTest`**

Remove all JSON-related tests. The service now takes only `invoiceNumber` and `xmlContent`.

```java
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
    @DisplayName("Happy path: signed XML → FOP → PDFBox → PDF/A-3 bytes returned")
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
    @DisplayName("Null xmlContent → InvoicePdfGenerationException before FOP")
    void generatePdf_nullXmlContent_throwsException() {
        assertThatThrownBy(() -> service.generatePdf(INVOICE_NUMBER, null))
                .isInstanceOf(InvoicePdfGenerationException.class)
                .hasMessageContaining("xmlContent");
        verifyNoInteractions(fopGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("Blank xmlContent → InvoicePdfGenerationException before FOP")
    void generatePdf_blankXmlContent_throwsException() {
        assertThatThrownBy(() -> service.generatePdf(INVOICE_NUMBER, "   "))
                .isInstanceOf(InvoicePdfGenerationException.class)
                .hasMessageContaining("xmlContent");
        verifyNoInteractions(fopGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("FOP throws PdfGenerationException → wrapped in InvoicePdfGenerationException")
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
    @DisplayName("PDFBox throws PdfConversionException → wrapped in InvoicePdfGenerationException")
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
```

- [ ] **Step 2: Update `FopInvoicePdfGeneratorTest` XML test data**

Change the test XML from flat `<invoice>` to CII format. Affects tests: `generatePdf_validXml_returnsPdfBytes`, `generatePdf_pdfExceedsMaxSize_throwsPdfGenerationException`.

Replace the flat XML in `generatePdf_validXml_returnsPdfBytes`:
```java
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
```

Replace the XML in `generatePdf_pdfExceedsMaxSize_throwsPdfGenerationException`:
```java
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
```

Also update `generatePdf_malformedXml_throwsPdfGenerationException` and the semaphore test — these use `<invoice/>` or non-XML strings that are fine as-is (malformed is malformed regardless of namespace).

- [ ] **Step 3: Update `SagaCommandHandlerTest`**

Changes needed:
1. `processCommand()` helper — remove the `"{}"` argument from the constructor call
2. All `pdfGenerationService.generatePdf(anyString(), anyString(), anyString())` → `pdfGenerationService.generatePdf(anyString(), anyString())`
3. The `verify` for `generatePdf` changes from 3 args to 2 args
4. Remove the `"{}"` from `handleProcessCommand_blankDocumentNumber`, `handleProcessCommand_blankDocumentId`, `handleProcessCommand_blankSignedXmlUrl` test commands

For the `processCommand()` helper:
```java
private ProcessInvoicePdfCommand processCommand() {
    return new ProcessInvoicePdfCommand(
            "saga-001", SagaStep.GENERATE_INVOICE_PDF, "corr-456",
            "doc-123", "INV-2024-001",
            SIGNED_XML_URL);
}
```

For all `generatePdf` mock/verify calls, change from:
```java
when(pdfGenerationService.generatePdf(anyString(), anyString(), anyString())).thenReturn(pdfBytes);
```
To:
```java
when(pdfGenerationService.generatePdf(anyString(), anyString())).thenReturn(pdfBytes);
```

And from:
```java
verify(pdfGenerationService).generatePdf("INV-2024-001", SIGNED_XML_CONTENT, "{}");
```
To:
```java
verify(pdfGenerationService).generatePdf("INV-2024-001", SIGNED_XML_CONTENT);
```

For the blank-validation test commands (e.g. `handleProcessCommand_blankDocumentNumber`):
```java
ProcessInvoicePdfCommand cmd = new ProcessInvoicePdfCommand(
        "saga-001", SagaStep.GENERATE_INVOICE_PDF, "corr-456",
        "doc-123", "   ", SIGNED_XML_URL);
```

- [ ] **Step 4: Run tests to verify**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-pdf-generation-service && mvn test
```

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/pdf/InvoicePdfGenerationServiceImplTest.java \
        src/test/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/pdf/FopInvoicePdfGeneratorTest.java \
        src/test/java/com/wpanther/invoice/pdf/application/service/SagaCommandHandlerTest.java
git commit -m "test: update tests for CII-XML-based PDF generation"
```

---

### Task 7: Remove unused config properties

**Files:**
- Modify: `src/main/resources/application.yml` (if `app.invoice.default-vat-rate` or `app.invoice.max-json-size-bytes` are defined)

- [ ] **Step 1: Check and remove unused properties**

Search `application.yml` for `app.invoice.default-vat-rate` and `app.invoice.max-json-size-bytes`. If present, remove them since `InvoicePdfGenerationServiceImpl` no longer uses them.

```bash
grep -n "default-vat-rate\|max-json-size" src/main/resources/application.yml
```

If found, remove those lines.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "chore: remove unused invoice JSON config properties"
```

---

### Task 8: Final verification

- [ ] **Step 1: Run full test suite with coverage**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-pdf-generation-service && mvn verify
```

Expected: All tests pass, JaCoCo coverage >= 90%.

- [ ] **Step 2: Build the service**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/invoice-pdf-generation-service && mvn clean package -DskipTests
```

Expected: BUILD SUCCESS.
