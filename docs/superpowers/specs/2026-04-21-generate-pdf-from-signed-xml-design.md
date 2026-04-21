# Generate PDF from Signed XML Instead of invoiceDataJson

**Date:** 2026-04-21
**Service:** invoice-pdf-generation-service (port 8090)

## Problem

The service receives two inputs for PDF generation: `signedXmlUrl` (the signed CrossIndustryInvoice XML) and `invoiceDataJson` (a JSON string with extracted invoice data). This creates two sources of truth — the JSON may drift from the actual signed XML, and the orchestrator must carry a large JSON payload through the saga.

## Solution

Derive the PDF directly from the signed CrossIndustryInvoice XML. Remove `invoiceDataJson` from the saga command entirely. Write a new XSL-FO template that matches CII elements via namespace-aware XPaths.

## Architecture Change

**Before:**
```
signedXmlUrl → download → signedXml ────────────────────────→ embed as PDF/A-3 attachment
invoiceDataJson → convertJsonToXml() → flat <invoice> XML → invoice.xsl → FOP → base PDF
```

**After:**
```
signedXmlUrl → download → signedXml → invoice.xsl (CII-aware) → FOP → base PDF
                                                    ↘ same signedXml → embed as PDF/A-3 attachment
```

## XSL-FO Template

### Namespaces

```xml
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
    xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2">
```

### Element Mapping

| PDF Section | New CII XPath |
|-------------|--------------|
| Invoice No. | `rsm:ExchangedDocument/ram:ID` (root match is on `rsm:Invoice_CrossIndustryInvoice`) |
| Invoice Date | `rsm:ExchangedDocument/ram:IssueDateTime` |
| Due Date | `ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime` |
| Seller Name | `ram:SellerTradeParty/ram:Name` |
| Seller Tax ID | `ram:SellerTradeParty/ram:SpecifiedTaxRegistration/ram:ID` |
| Seller Branch ID | `ram:SellerTradeParty/ram:ID` |
| Seller Address | Concatenation of `PostalTradeAddress/ram:LineOne`, `ram:CityName`, `ram:PostcodeCode` |
| Seller Phone | `ram:SellerTradeParty/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber` |
| Seller Email | `ram:SellerTradeParty/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID` |
| Buyer | Same pattern with `BuyerTradeParty` |
| Line items | `ram:IncludedSupplyChainTradeLineItem` |
| Item description | `ram:SpecifiedTradeProduct/ram:Name` |
| Item code | `ram:AssociatedDocumentLineDocument/ram:ID` |
| Quantity | `ram:SpecifiedLineTradeDelivery/ram:BilledQuantity` |
| Unit | `BilledQuantity/@unitCode` |
| Unit price | `ram:SpecifiedLineTradeAgreement/ram:GrossPriceProductTradePrice/ram:ChargeAmount` |
| Line amount | `ram:SpecifiedLineTradeSettlement/ram:LineTotalAmount` |
| Subtotal | `ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:LineTotalAmount` |
| VAT rate | `ram:ApplicableTradeTax/ram:RateApplicablePercent` |
| VAT amount | `ram:ApplicableTradeTax/ram:CalculatedAmount` |
| Grand total | `ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:GrandTotalAmount` |

### Layout

The page dimensions, fonts, colors, header/footer, and visual structure of the current template are preserved. Only the data-binding XPaths change.

### Dropped Fields

These fields are not available in the CII XML and will be removed from the template:

- `purchaseOrderNumber` — not a standard CII field
- `amountInWords` — not in CII
- `paymentInfo/bankName`, `accountNumber`, `accountName` — not in standard CII
- `notes` — not in standard CII
- `discount`, `amountBeforeVat` — no direct CII equivalent for header-level discounts

Missing CII elements produce empty output (XSL `value-of` returns empty string), consistent with current optional-field behavior.

## Java Code Changes

### Domain Layer

**`ProcessInvoicePdfCommand`** — remove `invoiceDataJson` field and constructor parameter.

**`InvoicePdfGenerationService`** — simplify method signature:
```java
byte[] generatePdf(String invoiceNumber, String xmlContent)
```

### Infrastructure Layer

**`InvoicePdfGenerationServiceImpl`** — remove JSON-to-XML conversion:
- Delete `convertJsonToXml()`, `writeElement()`, `getTextValue()` private methods
- Remove `ObjectMapper`, `defaultVatRate`, `maxJsonSizeBytes` constructor dependencies
- Remove `validateXmlWellFormedness()` — signed XML is already well-formed
- `generatePdf()` becomes: signed XML → FOP → PDF/A-3

**`SagaCommandHandler`** — remove `invoiceDataJson` references:
```java
// Before
pdfGenerationService.generatePdf(documentNumber, signedXml, command.getInvoiceDataJson())
// After
pdfGenerationService.generatePdf(documentNumber, signedXml)
```

**`KafkaProcessInvoicePdfCommand`** — remove `invoiceDataJson` field.

**`KafkaCommandMapper`** — remove `invoiceDataJson` from mapping.

### Upstream Changes (Outside This Service)

**Orchestrator** — stop including `invoiceDataJson` in `saga.command.invoice-pdf` commands. The orchestrator already has `signedXmlUrl`; no other payload changes needed.

### Test Changes

- Remove `InvoicePdfGenerationServiceImplTest` JSON-related test cases
- Add new tests with sample CII XML input
- Update `SagaCommandHandlerTest` to use 2-arg `generatePdf`
- Update `FopInvoicePdfGeneratorTest` if namespace handling changes

## Error Handling

No new exception types. Existing exceptions cover all cases:

- `FopInvoicePdfGenerator.PdfGenerationException` — FOP rendering failures
- `PdfA3Converter.PdfConversionException` — PDF/A-3 conversion failures
- `InvoicePdfGenerationException` — wraps both
- Saga retry/exhaustion logic in `SagaCommandHandler` unchanged

Remove JSON-specific validation (`invoiceDataJson null`, `maxJsonSizeBytes`, `seller/buyer missing in JSON`). The signed XML is validated upstream by document-intake and signed by xml-signing-service.

## Scope Summary

1. **New `invoice.xsl`** — rewritten with CII namespace-aware XPaths, same visual layout
2. **Remove `invoiceDataJson`** — from command DTOs, service interface, implementation, Kafka DTOs
3. **Simplify `InvoicePdfGenerationServiceImpl`** — remove JSON-to-XML conversion layer
4. **Upstream** — orchestrator stops sending `invoiceDataJson`
5. **No visual PDF changes** — same layout, same fonts, same structure
