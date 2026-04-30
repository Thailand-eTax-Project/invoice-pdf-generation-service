# invoice-pdf-generation-service Layer Separation Refactoring

**Date:** 2026-04-30
**Author:** Claude Code
**Status:** Draft

## Context

`invoice-pdf-generation-service` currently has saga infrastructure types (`SagaCommand`, `SagaReply`, `TraceEvent`) and Jackson serialization annotations placed in `domain/event/`. This violates Hexagonal/Port-Adapters architecture principles — the domain layer should have no framework or infrastructure dependencies.

The reference implementation is `taxinvoice-processing-service`, which correctly separates:
- Kafka DTOs with saga inheritance and Jackson annotations → `infrastructure/adapter/in/messaging/dto/`
- Use case interfaces with plain parameters → `application/port/in/`
- Pure domain events (no framework annotations) → `domain/event/`

## Problem Statement

Four domain event classes in `domain/event/` leak infrastructure concerns:

| File | Leaks |
|------|-------|
| `ProcessInvoicePdfCommand` | `extends SagaCommand`, `@JsonProperty`, `@JsonCreator`, `@Getter` |
| `CompensateInvoicePdfCommand` | `extends SagaCommand`, `@JsonProperty`, `@JsonCreator`, `@Getter` |
| `InvoicePdfReplyEvent` | `extends SagaReply` |
| `InvoicePdfGeneratedEvent` | `extends TraceEvent`, `@JsonProperty`, `@JsonCreator`, `@Getter` |

This violates the dependency rule: domain must not depend on infrastructure frameworks.

## Design

### Target Architecture

```
infrastructure/adapter/in/kafka/dto/
├── KafkaProcessInvoicePdfCommand    ← extends SagaCommand + Jackson (existing, rename/merge)
└── KafkaCompensateInvoicePdfCommand ← extends SagaCommand + Jackson (existing, rename/merge)

infrastructure/adapter/in/kafka/
├── SagaCommandHandler                ← (moved from application/service/)
└── KafkaCommandMapper                ← (DELETE — no longer needed, see §9)

application/port/in/
├── ProcessInvoicePdfUseCase          ← (refactored from usecase/)
└── CompensateInvoicePdfUseCase       ← (refactored from usecase/)

application/service/
└── InvoicePdfDocumentService         ← (unchanged)

infrastructure/adapter/out/messaging/
├── SagaReplyPublisher                ← (unchanged, inline InvoicePdfReplyEvent factory)
└── EventPublisher                    ← (unchanged)

application/dto/event/
└── InvoicePdfGeneratedEvent          ← (moved from domain/event/, extends TraceEvent)

domain/event/                         ← (empty after refactor — no pure domain events remain)

domain/
├── model/                            ← (unchanged: InvoicePdfDocument, GenerationStatus)
├── service/InvoicePdfGenerationService ← (unchanged)
├── repository/                       ← (unchanged)
└── exception/                        ← (unchanged)
```

### Changes

#### 1. `infrastructure/adapter/in/kafka/dto/`

**Rename and consolidate:**
- `KafkaProcessInvoicePdfCommand` → `ProcessInvoicePdfCommand` (replaces `domain/event/ProcessInvoicePdfCommand`)
- `KafkaCompensateInvoicePdfCommand` → `CompensateInvoicePdfCommand` (replaces `domain/event/CompensateInvoicePdfCommand`)
- Move Jackson annotations with the DTOs (they stay as Kafka deserialization concerns)

Package: `com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka.dto`

#### 2. `application/port/in/`

**Refactor use case interfaces** (existing files in `application/usecase/` are renamed and moved):

```java
public interface ProcessInvoicePdfUseCase {
    void handle(String documentId, String documentNumber, String signedXmlUrl,
                String sagaId, SagaStep sagaStep, String correlationId);
}

public interface CompensateInvoicePdfUseCase {
    void handle(String documentId, String sagaId, SagaStep sagaStep, String correlationId);
}
```

Package: `com.wpanther.invoice.pdf.application.port.in`

#### 3. `infrastructure/adapter/in/kafka/SagaCommandHandler`

**Move from** `application/service/SagaCommandHandler.java`
**To** `infrastructure/adapter/in/kafka/SagaCommandHandler.java`

Handles Kafka DTO → use case call translation. Calls `ProcessInvoicePdfUseCase.handle(docId, docNumber, signedXmlUrl, sagaId, step, corrId)` and `CompensateInvoicePdfUseCase.handle(docId, sagaId, step, corrId)` with extracted parameters — no command objects passed into domain/application layers.

#### 4. `application/service/InvoicePdfDocumentService`

**Modify method signatures.** Methods that currently accept command objects (`ProcessInvoicePdfCommand` / `CompensateInvoicePdfCommand`) are updated to accept individual fields. See §12 for the full list of new signatures.

#### 5. `application/dto/event/InvoicePdfGeneratedEvent`

**Move from** `domain/event/InvoicePdfGeneratedEvent`
**To** `application/dto/event/InvoicePdfGeneratedEvent`

This class extends `TraceEvent` from the saga library — it's a notification DTO, not a pure domain event. Its proper home is `application/dto/event/`.

Package: `com.wpanther.invoice.pdf.application.dto.event`

#### 6. `application/service/SagaCommandHandler` — DELETE

After moving the routing logic to `infrastructure/adapter/in/kafka/SagaCommandHandler`, delete `application/service/SagaCommandHandler.java`.

#### 7. `domain/event/` — DELETE

After moving `InvoicePdfGeneratedEvent`, delete:
- `domain/event/ProcessInvoicePdfCommand.java`
- `domain/event/CompensateInvoicePdfCommand.java`
- `domain/event/InvoicePdfGeneratedEvent.java`
- `domain/event/InvoicePdfReplyEvent.java` (consolidated into `SagaReplyPublisher` as inline factory)

#### 8. `infrastructure/adapter/out/messaging/SagaReplyPublisher`

**Unchanged**, but inline the `InvoicePdfReplyEvent` factory methods since the class is gone from domain:

```java
// InvoicePdfReplyEvent.reply factory moved here as private methods or static helpers
// Calls remain the same — just the factory location changes
```

#### 9. `infrastructure/adapter/in/kafka/KafkaCommandMapper` — DELETE

The mapper becomes unnecessary since `SagaCommandHandler` directly extracts fields from DTOs and passes them as parameters. Remove after confirming no other usage.

#### 10. `application/usecase/` — DELETE

After moving interfaces to `application/port/in/`, delete:
- `application/usecase/ProcessInvoicePdfUseCase.java`
- `application/usecase/CompensateInvoicePdfUseCase.java`

#### 11. `application/port/out/` — UNCHANGED

Ports remain in `application/port/out/`:
- `PdfStoragePort`
- `SagaReplyPort`
- `PdfEventPort`
- `SignedXmlFetchPort`

#### 12. `InvoicePdfDocumentService` method signatures

Methods that currently accept `ProcessInvoicePdfCommand` / `CompensateInvoicePdfCommand` are updated to accept individual fields:

| Method | New Signature |
|--------|---------------|
| `completeGenerationAndPublish` | `(UUID id, String s3Key, String fileUrl, long fileSize, int retryCount, String documentId, String documentNumber, String sagaId, SagaStep sagaStep, String correlationId)` |
| `failGenerationAndPublish` | `(UUID id, String error, int retryCount, String sagaId, SagaStep sagaStep, String correlationId)` |
| `publishIdempotentSuccess` | `(InvoicePdfDocument doc, String documentId, String documentNumber, String sagaId, SagaStep sagaStep, String correlationId)` |
| `publishRetryExhausted` | `(String sagaId, SagaStep sagaStep, String correlationId, String documentId, String documentNumber)` |
| `publishGenerationFailure` | `(String sagaId, SagaStep sagaStep, String correlationId, String error)` |
| `publishCompensated` | `(String sagaId, SagaStep sagaStep, String correlationId)` |
| `publishCompensationFailure` | `(String sagaId, SagaStep sagaStep, String correlationId, String error)` |
| `buildGeneratedEvent` | `(InvoicePdfDocument doc, String documentId, String documentNumber, String sagaId, String correlationId)` |

## Files to Modify

| Action | File |
|--------|------|
| Rename+move | `infrastructure/adapter/in/kafka/KafkaProcessInvoicePdfCommand.java` → `dto/ProcessInvoicePdfCommand.java` |
| Rename+move | `infrastructure/adapter/in/kafka/KafkaCompensateInvoicePdfCommand.java` → `dto/CompensateInvoicePdfCommand.java` |
| Rename+move | `application/usecase/ProcessInvoicePdfUseCase.java` → `port/in/ProcessInvoicePdfUseCase.java` (signature changes) |
| Rename+move | `application/usecase/CompensateInvoicePdfUseCase.java` → `port/in/CompensateInvoicePdfUseCase.java` (signature changes) |
| Move | `application/service/SagaCommandHandler.java` → `infrastructure/adapter/in/kafka/SagaCommandHandler.java` |
| Move | `domain/event/InvoicePdfGeneratedEvent.java` → `application/dto/event/InvoicePdfGeneratedEvent.java` |
| Delete | `infrastructure/adapter/in/kafka/KafkaCommandMapper.java` |
| Delete | `domain/event/ProcessInvoicePdfCommand.java` |
| Delete | `domain/event/CompensateInvoicePdfCommand.java` |
| Delete | `domain/event/InvoicePdfReplyEvent.java` |
| Delete | `domain/event/InvoicePdfGeneratedEvent.java` (original location) |
| Delete | `application/usecase/ProcessInvoicePdfUseCase.java` (original location) |
| Delete | `application/usecase/CompensateInvoicePdfUseCase.java` (original location) |
| Modify | `InvoicePdfDocumentService.java` (update method signatures) |
| Modify | `SagaRouteConfig.java` (remove KafkaCommandMapper usage) |
| Modify | `SagaReplyPublisher.java` (inline `InvoicePdfReplyEvent` factory) |
| Modify | `EventPublisher.java` (import new package path for `InvoicePdfGeneratedEvent`) |

## Testing Strategy

1. **Unit tests** — Update all test classes that reference the moved classes:
   - `SagaCommandHandlerTest` → update import for moved handler
   - `InvoicePdfDocumentServiceTest` → update command parameter types
   - `InvoicePdfGeneratedEvent` test → update package import

2. **Camel route tests** — `SagaRouteConfigTest` needs import updates

3. **Kafka consumer tests** — verify `ProcessInvoicePdfCommand` / `CompensateInvoicePdfCommand` deserialization still works

4. **Integration test** — Run full service against test infrastructure to verify saga orchestration still functions

## Verification

After refactoring, run:
```bash
mvn clean compile   # Verify no compilation errors
mvn clean test      # Verify all tests pass
```

## Scope

This refactor addresses **only** `invoice-pdf-generation-service`. Other services with similar patterns (e.g., `document-storage-service`, `notification-service`) are out of scope and should be audited separately.