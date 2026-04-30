# Design: Rename invoiceId/invoiceNumber to documentId/documentNumber in Contract Events

**Date**: 2026-04-08
**Status**: Approved
**Scope**: invoice-pdf-generation-service contract events only

## Background

The taxinvoice-processing-service recently renamed `invoiceId`/`invoiceNumber` to `documentId`/`documentNumber` in all Kafka contract events (commands, replies, notifications). The orchestrator will send `documentId`/`documentNumber` fields going forward. The invoice-pdf-generation-service must follow the same pattern to consume and publish Kafka messages correctly.

Key fact: the current `documentId` and `invoiceId` fields in `ProcessInvoicePdfCommand` carry the **same value** from the orchestrator. The `invoiceId` field is redundant.

## Pattern (from taxinvoice-processing-service)

| Concept | Contract Event Field | Domain Model Field |
|---------|---------------------|-------------------|
| Primary identifier | `documentId` | `invoiceId` (unchanged) |
| Display number | `documentNumber` | `invoiceNumber` (unchanged) |

The mapping from contract fields to domain fields happens in `SagaCommandHandler` and `InvoicePdfDocumentService`.

## Changes

### Contract Event Classes (domain/event/)

**ProcessInvoicePdfCommand:**
- Remove `invoiceId` field
- Rename `invoiceNumber` → `documentNumber`
- Update both constructors and `@JsonProperty` annotations

**CompensateInvoicePdfCommand:**
- Remove `invoiceId` field (keep `documentId` only)
- Update both constructors

**InvoicePdfGeneratedEvent:**
- Remove `invoiceId` field
- Rename `invoiceNumber` → `documentNumber`
- Update both constructors

### Kafka DTOs (infrastructure/adapter/in/kafka/)

**KafkaProcessInvoicePdfCommand:**
- Remove `invoiceId` field
- Rename `invoiceNumber` → `documentNumber`
- Update both constructors

**KafkaCompensateInvoicePdfCommand:**
- Remove `invoiceId` field
- Update both constructors

**KafkaCommandMapper:**
- Remove `invoiceId` from `toProcess()` mapping
- Change `src.getInvoiceNumber()` → `src.getDocumentNumber()`
- Remove `invoiceId` from `toCompensate()` mapping

### Application Service

**SagaCommandHandler:**
- `command.getInvoiceId()` → `command.getDocumentId()`
- `command.getInvoiceNumber()` → `command.getDocumentNumber()`
- MDC key `invoiceId` → `documentId`
- MDC key `invoiceNumber` → `documentNumber`
- Update all log messages

**InvoicePdfDocumentService:**
- `buildGeneratedEvent()`: remove `invoiceId` param, use `documentId`/`documentNumber` from command
- `publishRetryExhausted()`: `command.getInvoiceId()` → `command.getDocumentId()`, `command.getInvoiceNumber()` → `command.getDocumentNumber()`

### Messaging

**EventPublisher:**
- `event.getInvoiceId()` → `event.getDocumentId()` for aggregateId and partitionKey

### Unchanged Files

- `InvoicePdfDocument` (domain model) — keeps `invoiceId`/`invoiceNumber`
- `InvoicePdfDocumentEntity` / `InvoicePdfDocumentRepositoryAdapter` / `JpaInvoicePdfDocumentRepository` — database layer unchanged
- `InvoicePdfReplyEvent` — has no `invoiceId`/`invoiceNumber` fields
- `SagaReplyPublisher` — no invoice-specific fields
- Database schema (Flyway migrations) — column names stay the same
- `FopInvoicePdfGenerator`, `PdfA3Converter`, `InvoicePdfGenerationServiceImpl` — no contract field references

## Test Updates

All test files referencing `invoiceId`/`invoiceNumber` on command/event classes must be updated:
- Constructor calls: remove `invoiceId` arg, rename `invoiceNumber` → `documentNumber`
- Getter calls: `getInvoiceId()` → `getDocumentId()`, `getInvoiceNumber()` → `getDocumentNumber()`
- MDC key assertions if applicable

## Verification

1. `mvn clean compile` — all source compiles
2. `mvn test` — all tests pass
3. Manual review that no `invoiceId` or `invoiceNumber` remains in contract event classes
