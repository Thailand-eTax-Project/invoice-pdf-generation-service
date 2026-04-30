# invoice-pdf-generation-service Layer Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor invoice-pdf-generation-service to place saga infrastructure types in `infrastructure/` and use case interfaces with plain parameters in `application/port/in/`, matching the architecture of taxinvoice-processing-service.

**Architecture:** Kafka DTOs (SagaCommand/SagaReply + Jackson) live in `infrastructure/adapter/in/kafka/dto/`. Use case interfaces accept plain field parameters. `SagaCommandHandler` routes DTOs to use cases by extracting fields — no command objects flow into domain or application layers. `InvoicePdfGeneratedEvent` moves to `application/dto/event/` as it extends TraceEvent (notification DTO, not domain).

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, saga-commons library, Jackson

---

## File Changes Overview

```
CREATING:
  infrastructure/adapter/in/kafka/dto/ProcessInvoicePdfCommand.java       (rename from Kafka*Command)
  infrastructure/adapter/in/kafka/dto/CompensateInvoicePdfCommand.java    (rename from Kafka*Command)
  application/port/in/ProcessInvoicePdfUseCase.java                        (new interface, plain params)
  application/port/in/CompensateInvoicePdfUseCase.java                     (new interface, plain params)

MOVING:
  SagaCommandHandler.java          application/service/ → infrastructure/adapter/in/kafka/
  InvoicePdfGeneratedEvent.java     domain/event/ → application/dto/event/

DELETING:
  domain/event/ProcessInvoicePdfCommand.java
  domain/event/CompensateInvoicePdfCommand.java
  domain/event/InvoicePdfReplyEvent.java
  domain/event/InvoicePdfGeneratedEvent.java (original)
  application/service/SagaCommandHandler.java (original location)
  application/usecase/ProcessInvoicePdfUseCase.java
  application/usecase/CompensateInvoicePdfUseCase.java
  infrastructure/adapter/in/kafka/KafkaCommandMapper.java

MODIFYING:
  InvoicePdfDocumentService.java    (method signatures change from command objects to plain fields)
  SagaRouteConfig.java               (remove KafkaCommandMapper, use new DTO names)
  SagaReplyPublisher.java           (inline InvoicePdfReplyEvent factory)
  EventPublisher.java               (import new package path)
```

---

## Before You Start

- Build the service to confirm it compiles: `mvn clean compile -q`
- Run tests to confirm baseline: `mvn clean test -q`
- Work inside the service directory: `/home/wpanther/projects/etax/invoice-microservices/services/invoice-pdf-generation-service`

---

## Task 1: Create `dto/` directory and new `ProcessInvoicePdfCommand`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/dto/ProcessInvoicePdfCommand.java`
- Source: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaProcessInvoicePdfCommand.java`

- [ ] **Step 1: Write the new file**

Create directory and new file:
```bash
mkdir -p src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/dto
```

Create `ProcessInvoicePdfCommand.java` — this is a rename of `KafkaProcessInvoicePdfCommand` with package changed:
```java
package com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka.dto;

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

- [ ] **Step 2: Verify it compiles**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No errors related to the new file

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/dto/ProcessInvoicePdfCommand.java
git commit -m "refactor: rename KafkaProcessInvoicePdfCommand to ProcessInvoicePdfCommand in dto/ package"
```

---

## Task 2: Create `CompensateInvoicePdfCommand` in `dto/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/dto/CompensateInvoicePdfCommand.java`
- Source: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaCompensateInvoicePdfCommand.java`

- [ ] **Step 1: Write the new file**

```java
package com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public class CompensateInvoicePdfCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonCreator
    public CompensateInvoicePdfCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = Objects.requireNonNull(documentId, "documentId is required");
    }

    public CompensateInvoicePdfCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                        String documentId) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = Objects.requireNonNull(documentId, "documentId is required");
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/dto/CompensateInvoicePdfCommand.java
git commit -m "refactor: rename KafkaCompensateInvoicePdfCommand to CompensateInvoicePdfCommand in dto/ package"
```

---

## Task 3: Create `ProcessInvoicePdfUseCase` in `application/port/in/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/pdf/application/port/in/ProcessInvoicePdfUseCase.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/application/usecase/ProcessInvoicePdfUseCase.java` (later)
- Modify: `SagaCommandHandler` (later in Task 5)

- [ ] **Step 1: Write the new interface**

```java
package com.wpanther.invoice.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port for invoice PDF generation.
 * Called by SagaCommandHandler with plain fields — no command objects.
 */
public interface ProcessInvoicePdfUseCase {

    void handle(String documentId, String documentNumber, String signedXmlUrl,
                String sagaId, SagaStep sagaStep, String correlationId);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/application/port/in/ProcessInvoicePdfUseCase.java
git commit -m "refactor: add ProcessInvoicePdfUseCase in application/port/in/ with plain parameter signatures"
```

---

## Task 4: Create `CompensateInvoicePdfUseCase` in `application/port/in/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/pdf/application/port/in/CompensateInvoicePdfUseCase.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/application/usecase/CompensateInvoicePdfUseCase.java` (later in Task 10)

- [ ] **Step 1: Write the new interface**

```java
package com.wpanther.invoice.pdf.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

/**
 * Inbound port for invoice PDF compensation.
 * Called by SagaCommandHandler with plain fields — no command objects.
 */
public interface CompensateInvoicePdfUseCase {

    void handle(String documentId, String sagaId, SagaStep sagaStep, String correlationId);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn compile -q 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/application/port/in/CompensateInvoicePdfUseCase.java
git commit -m "refactor: add CompensateInvoicePdfUseCase in application/port/in/ with plain parameter signatures"
```

---

## Task 5: Rewrite `SagaCommandHandler` in `infrastructure/adapter/in/kafka/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/SagaCommandHandler.java` (new location)
- Delete: `src/main/java/com/wpanther/invoice/pdf/application/service/SagaCommandHandler.java` (later in Task 10)
- Modify: `InvoicePdfDocumentService.java` (Task 7)

This is the most complex rewrite. The handler now:
1. Accepts `ProcessInvoicePdfCommand` / `CompensateInvoicePdfCommand` from Kafka
2. Extracts all fields and calls use case interfaces with plain parameters
3. Calls `InvoicePdfDocumentService` with plain parameters (not command objects)

- [ ] **Step 1: Write the new SagaCommandHandler**

```java
package com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka;

import com.wpanther.invoice.pdf.application.port.in.CompensateInvoicePdfUseCase;
import com.wpanther.invoice.pdf.application.port.in.ProcessInvoicePdfUseCase;
import com.wpanther.invoice.pdf.application.service.InvoicePdfDocumentService;
import com.wpanther.invoice.pdf.application.port.out.PdfStoragePort;
import com.wpanther.invoice.pdf.application.port.out.SagaReplyPort;
import com.wpanther.invoice.pdf.application.port.out.SignedXmlFetchPort;
import com.wpanther.invoice.pdf.domain.model.InvoicePdfDocument;
import com.wpanther.invoice.pdf.domain.service.InvoicePdfGenerationService;
import com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka.dto.CompensateInvoicePdfCommand;
import com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka.dto.ProcessInvoicePdfCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Saga command handler — driving adapter that receives Kafka messages and calls use cases.
 * No command objects flow into domain or application layers — only plain field parameters.
 */
@Service
@Slf4j
public class SagaCommandHandler implements ProcessInvoicePdfUseCase, CompensateInvoicePdfUseCase {

    private static final String MDC_SAGA_ID         = "sagaId";
    private static final String MDC_CORRELATION_ID  = "correlationId";
    private static final String MDC_DOCUMENT_NUMBER = "documentNumber";
    private static final String MDC_DOCUMENT_ID     = "documentId";

    private final InvoicePdfDocumentService pdfDocumentService;
    private final InvoicePdfGenerationService pdfGenerationService;
    private final PdfStoragePort pdfStoragePort;
    private final SagaReplyPort sagaReplyPort;
    private final SignedXmlFetchPort signedXmlFetchPort;
    private final int maxRetries;

    public SagaCommandHandler(InvoicePdfDocumentService pdfDocumentService,
                              InvoicePdfGenerationService pdfGenerationService,
                              PdfStoragePort pdfStoragePort,
                              SagaReplyPort sagaReplyPort,
                              SignedXmlFetchPort signedXmlFetchPort,
                              @Value("${app.pdf.generation.max-retries:3}") int maxRetries) {
        this.pdfDocumentService = pdfDocumentService;
        this.pdfGenerationService = pdfGenerationService;
        this.pdfStoragePort = pdfStoragePort;
        this.sagaReplyPort = sagaReplyPort;
        this.signedXmlFetchPort = signedXmlFetchPort;
        this.maxRetries = maxRetries;
    }

    @Override
    public void handle(String documentId, String documentNumber, String signedXmlUrl,
                       String sagaId, SagaStep sagaStep, String correlationId) {
        MDC.put(MDC_SAGA_ID,        sagaId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_DOCUMENT_NUMBER, documentNumber);
        MDC.put(MDC_DOCUMENT_ID,     documentId);
        try {
            log.info("Handling ProcessInvoicePdfCommand for saga {} document {}", sagaId, documentNumber);
            try {
                if (signedXmlUrl == null || signedXmlUrl.isBlank()) {
                    pdfDocumentService.publishGenerationFailure(sagaId, sagaStep, correlationId, "signedXmlUrl is null or blank in saga command");
                    return;
                }
                if (documentNumber == null || documentNumber.isBlank()) {
                    pdfDocumentService.publishGenerationFailure(sagaId, sagaStep, correlationId, "documentNumber is null or blank in saga command");
                    return;
                }
                if (documentId == null || documentId.isBlank()) {
                    pdfDocumentService.publishGenerationFailure(sagaId, sagaStep, correlationId, "documentId is null or blank in saga command");
                    return;
                }

                Optional<InvoicePdfDocument> existing = pdfDocumentService.findByInvoiceId(documentId);

                if (existing.isPresent() && existing.get().isCompleted()) {
                    pdfDocumentService.publishIdempotentSuccess(existing.get(), documentId, documentNumber, sagaId, sagaStep, correlationId);
                    return;
                }

                int previousRetryCount = existing.map(InvoicePdfDocument::getRetryCount).orElse(-1);

                if (existing.isPresent()) {
                    InvoicePdfDocument prior = existing.get();
                    if (!prior.isFailed()) {
                        log.warn("Found document in non-terminal state (status={}) for document {} saga {} — TX2 may have rolled back; will delete and retry",
                                prior.getStatus(), documentId, sagaId);
                    }
                    if (prior.isMaxRetriesExceeded(maxRetries)) {
                        pdfDocumentService.publishRetryExhausted(sagaId, sagaStep, correlationId, documentId, documentNumber);
                        return;
                    }
                }

                InvoicePdfDocument document;
                if (existing.isPresent()) {
                    document = pdfDocumentService.replaceAndBeginGeneration(
                            existing.get().getId(), previousRetryCount, documentId, documentNumber);
                } else {
                    document = pdfDocumentService.beginGeneration(documentId, documentNumber);
                }

                String s3Key = null;
                try {
                    String xml = signedXmlFetchPort.fetch(signedXmlUrl);
                    byte[] pdfBytes = pdfGenerationService.generatePdf(documentNumber, xml);
                    s3Key = pdfStoragePort.store(documentNumber, pdfBytes);
                    String fileUrl = pdfStoragePort.resolveUrl(s3Key);

                    pdfDocumentService.completeGenerationAndPublish(
                            document.getId(), s3Key, fileUrl, pdfBytes.length,
                            previousRetryCount, documentId, documentNumber, sagaId, sagaStep, correlationId);

                    log.debug("Successfully processed PDF generation for saga {} document {}", sagaId, documentNumber);

                } catch (CallNotPermittedException e) {
                    log.warn("MinIO circuit breaker OPEN for saga {} document {} — no upload attempted, will retry when CB re-closes: {}",
                            sagaId, documentNumber, e.getMessage());
                    pdfDocumentService.failGenerationAndPublish(
                            document.getId(), "MinIO circuit breaker open: " + e.getMessage(),
                            previousRetryCount, sagaId, sagaStep, correlationId);

                } catch (Exception e) {
                    if (s3Key != null) {
                        try {
                            pdfStoragePort.delete(s3Key);
                            log.warn("Deleted orphaned MinIO object {} after processing failure for saga {}",
                                    s3Key, sagaId);
                        } catch (Exception deleteEx) {
                            log.error("[ORPHAN_PDF] s3Key={} saga={} documentNumber={} error={} — manual recovery required: delete object from MinIO bucket",
                                    s3Key, sagaId, documentNumber, describeThrowable(deleteEx));
                        }
                    }
                    log.error("PDF generation/upload failed for saga {} document {}: {}",
                            sagaId, documentNumber, e.getMessage(), e);
                    pdfDocumentService.failGenerationAndPublish(
                            document.getId(), describeThrowable(e), previousRetryCount, sagaId, sagaStep, correlationId);
                }

            } catch (OptimisticLockingFailureException e) {
                log.warn("Concurrent modification conflict for saga {} document {} — retryable: {}",
                        sagaId, documentNumber, e.getMessage());
                pdfDocumentService.publishGenerationFailure(sagaId, sagaStep, correlationId, "Concurrent modification conflict: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error for saga {} document {}: {}", sagaId, documentNumber, e.getMessage(), e);
                pdfDocumentService.publishGenerationFailure(sagaId, sagaStep, correlationId, describeThrowable(e));
            }
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void handle(String documentId, String sagaId, SagaStep sagaStep, String correlationId) {
        MDC.put(MDC_SAGA_ID,        sagaId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_DOCUMENT_ID,     documentId);
        try {
            log.info("Handling compensation for saga {} document {}", sagaId, documentId);
            try {
                Optional<InvoicePdfDocument> existing = pdfDocumentService.findByInvoiceId(documentId);

                if (existing.isPresent()) {
                    InvoicePdfDocument document = existing.get();
                    pdfDocumentService.deleteById(document.getId());
                    if (document.getDocumentPath() != null) {
                        try {
                            pdfStoragePort.delete(document.getDocumentPath());
                        } catch (Exception e) {
                            log.warn("Failed to delete PDF from MinIO for saga {} key {}: {}",
                                    sagaId, document.getDocumentPath(), e.getMessage());
                        }
                    }
                    log.info("Compensated InvoicePdfDocument {} for saga {}", document.getId(), sagaId);
                } else {
                    log.info("No document found for documentId {} — already compensated or never processed", documentId);
                }

                pdfDocumentService.publishCompensated(sagaId, sagaStep, correlationId);

            } catch (Exception e) {
                log.error("Failed to compensate for saga {} document {}: {}", sagaId, documentId, e.getMessage(), e);
                pdfDocumentService.publishCompensationFailure(sagaId, sagaStep, correlationId, "Compensation failed: " + describeThrowable(e));
            }
        } finally {
            MDC.clear();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrchestrationFailure(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            String error = "Message routed to DLQ after retry exhaustion: " + describeThrowable(cause);
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, error);
            log.error("Published FAILURE reply after DLQ routing for saga {}", sagaId);
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of DLQ failure for saga {} — orchestrator must timeout", sagaId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrchestrationFailureForUnparsedMessage(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            String error = "Message routed to DLQ after deserialization failure: " + describeThrowable(cause);
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, error);
            log.error("Published FAILURE reply after DLQ routing (deserialization failure) for saga {}", sagaId);
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of DLQ deserialization failure for saga {} — orchestrator must timeout", sagaId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishCompensationOrchestrationFailure(String sagaId, SagaStep sagaStep, String correlationId, Throwable cause) {
        try {
            String error = "Compensation message routed to DLQ after retry exhaustion: " + describeThrowable(cause);
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, error);
            log.error("Published FAILURE reply after compensation DLQ routing for saga {}", sagaId);
        } catch (Exception e) {
            log.error("Cannot notify orchestrator of compensation DLQ failure for saga {} — orchestrator must timeout", sagaId, e);
        }
    }

    private String describeThrowable(Throwable t) {
        if (t == null) return "unknown error";
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }
}
```

- [ ] **Step 2: Compile and fix any errors**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Compile errors in `InvoicePdfDocumentService` (method signatures don't match yet) — this is expected

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/SagaCommandHandler.java
git commit -m "refactor: move SagaCommandHandler to infrastructure/adapter/in/kafka/ with plain parameter calls"
```

---

## Task 6: Move `InvoicePdfGeneratedEvent` to `application/dto/event/`

**Files:**
- Create: `src/main/java/com/wpanther/invoice/pdf/application/dto/event/InvoicePdfGeneratedEvent.java`
- Modify: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/messaging/EventPublisher.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/domain/event/InvoicePdfGeneratedEvent.java` (original, after verifying)
- Delete: `src/main/java/com/wpanther/invoice/pdf/domain/event/InvoicePdfReplyEvent.java` (Task 9)

- [ ] **Step 1: Create the new file**

```java
package com.wpanther.invoice.pdf.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class InvoicePdfGeneratedEvent extends TraceEvent {

    private static final String EVENT_TYPE = "pdf.generated.invoice";
    private static final String SOURCE = "invoice-pdf-generation-service";
    private static final String TRACE_TYPE = "PDF_GENERATED";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("documentUrl")
    private final String documentUrl;

    @JsonProperty("fileSize")
    private final long fileSize;

    @JsonProperty("xmlEmbedded")
    private final boolean xmlEmbedded;

    public InvoicePdfGeneratedEvent(
            String sagaId,
            String documentId,
            String documentNumber,
            String documentUrl,
            long fileSize,
            boolean xmlEmbedded,
            String correlationId) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public InvoicePdfGeneratedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("source") String source,
            @JsonProperty("traceType") String traceType,
            @JsonProperty("context") String context,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("documentUrl") String documentUrl,
            @JsonProperty("fileSize") long fileSize,
            @JsonProperty("xmlEmbedded") boolean xmlEmbedded,
            @JsonProperty("correlationId") String correlationId) {
        super(eventId, occurredAt, eventType, version, null, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
    }
}
```

- [ ] **Step 2: Update EventPublisher import**

Change the import in `EventPublisher.java` from:
```java
import com.wpanther.invoice.pdf.domain.event.InvoicePdfGeneratedEvent;
```
to:
```java
import com.wpanther.invoice.pdf.application.dto.event.InvoicePdfGeneratedEvent;
```

- [ ] **Step 3: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Errors in `InvoicePdfDocumentService` (will fix in Task 7)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/application/dto/event/InvoicePdfGeneratedEvent.java
git add src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/messaging/EventPublisher.java
git commit -m "refactor: move InvoicePdfGeneratedEvent to application/dto/event/"
```

---

## Task 7: Update `InvoicePdfDocumentService` method signatures

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/pdf/application/service/InvoicePdfDocumentService.java`

This is the largest change — update all method signatures to accept plain fields instead of command objects.

- [ ] **Step 1: Rewrite InvoicePdfDocumentService.java with updated method signatures**

Full file rewrite. The key changes:
- Remove imports for `ProcessInvoicePdfCommand`, `CompensateInvoicePdfCommand` from domain.event
- Add import for `com.wpanther.saga.domain.enums.SagaStep`
- All methods that took command objects now take individual field parameters

```java
package com.wpanther.invoice.pdf.application.service;

import com.wpanther.invoice.pdf.application.port.out.PdfEventPort;
import com.wpanther.invoice.pdf.application.port.out.SagaReplyPort;
import com.wpanther.invoice.pdf.application.dto.event.InvoicePdfGeneratedEvent;
import com.wpanther.invoice.pdf.domain.model.InvoicePdfDocument;
import com.wpanther.invoice.pdf.domain.repository.InvoicePdfDocumentRepository;
import com.wpanther.invoice.pdf.infrastructure.metrics.PdfGenerationMetrics;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class InvoicePdfDocumentService {

    private final InvoicePdfDocumentRepository repository;
    private final PdfEventPort pdfEventPort;
    private final SagaReplyPort sagaReplyPort;

    @Autowired(required = false)
    private PdfGenerationMetrics pdfGenerationMetrics;

    public InvoicePdfDocumentService(InvoicePdfDocumentRepository repository,
                                     PdfEventPort pdfEventPort,
                                     SagaReplyPort sagaReplyPort,
                                     PdfGenerationMetrics pdfGenerationMetrics) {
        this.repository = repository;
        this.pdfEventPort = pdfEventPort;
        this.sagaReplyPort = sagaReplyPort;
        this.pdfGenerationMetrics = pdfGenerationMetrics;
    }

    @Transactional(readOnly = true)
    public Optional<InvoicePdfDocument> findByInvoiceId(String invoiceId) {
        return repository.findByInvoiceId(invoiceId);
    }

    @Transactional
    public InvoicePdfDocument beginGeneration(String invoiceId, String invoiceNumber) {
        log.info("Initiating PDF generation for invoice: {}", invoiceNumber);
        InvoicePdfDocument document = InvoicePdfDocument.builder()
                .invoiceId(invoiceId)
                .invoiceNumber(invoiceNumber)
                .build();
        document.startGeneration();
        return repository.save(document);
    }

    @Transactional
    public InvoicePdfDocument replaceAndBeginGeneration(
            UUID existingId, int previousRetryCount, String invoiceId, String invoiceNumber) {
        log.info("Replacing PDF document {} and initiating new generation for invoice: {}", existingId, invoiceNumber);
        repository.deleteById(existingId);
        repository.flush();
        InvoicePdfDocument document = InvoicePdfDocument.builder()
                .invoiceId(invoiceId)
                .invoiceNumber(invoiceNumber)
                .build();
        document.startGeneration();
        document.incrementRetryCountTo(previousRetryCount + 1);
        return repository.save(document);
    }

    @Transactional
    public void completeGenerationAndPublish(UUID documentId, String s3Key, String fileUrl,
                                             long fileSize, int previousRetryCount,
                                             String documentId, String documentNumber,
                                             String sagaId, SagaStep sagaStep, String correlationId) {
        InvoicePdfDocument doc = requireDocument(documentId);
        doc.markCompleted(s3Key, fileUrl, fileSize, LocalDateTime.now());
        doc.markXmlEmbedded();
        applyRetryCount(doc, previousRetryCount);
        doc = repository.save(doc);

        pdfEventPort.publishPdfGenerated(buildGeneratedEvent(doc, documentId, documentNumber, sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId, doc.getDocumentUrl(), doc.getFileSize());

        log.info("Completed PDF generation and published events for saga {} invoice {}", sagaId, doc.getInvoiceNumber());
    }

    @Transactional
    public void failGenerationAndPublish(UUID documentId, String errorMessage,
                                         int previousRetryCount,
                                         String sagaId, SagaStep sagaStep, String correlationId) {
        String safeError = errorMessage != null ? errorMessage : "PDF generation failed";
        InvoicePdfDocument doc = requireDocument(documentId);
        doc.markFailed(safeError, LocalDateTime.now());
        applyRetryCount(doc, previousRetryCount);
        repository.save(doc);

        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, safeError);

        log.warn("PDF generation failed for saga {} invoice {}: {}", sagaId, doc.getInvoiceNumber(), safeError);
    }

    @Transactional
    public void deleteById(UUID documentId) {
        repository.deleteById(documentId);
        repository.flush();
    }

    @Transactional
    public void publishIdempotentSuccess(InvoicePdfDocument existing,
                                         String documentId, String documentNumber,
                                         String sagaId, SagaStep sagaStep, String correlationId) {
        pdfEventPort.publishPdfGenerated(buildGeneratedEvent(existing, documentId, documentNumber, sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId, existing.getDocumentUrl(), existing.getFileSize());
        log.warn("PDF already generated for saga {} — re-publishing SUCCESS reply", sagaId);
    }

    @Transactional
    public void publishRetryExhausted(String sagaId, SagaStep sagaStep, String correlationId,
                                      String documentId, String documentNumber) {
        if (pdfGenerationMetrics != null) {
            pdfGenerationMetrics.recordRetryExhausted(sagaId, documentId, documentNumber);
        }
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, "Maximum retry attempts exceeded");
        log.error("Max retries exceeded for saga {} document {}", sagaId, documentNumber);
    }

    @Transactional
    public void publishGenerationFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, errorMessage);
    }

    @Transactional
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
    }

    @Transactional
    public void publishCompensationFailure(String sagaId, SagaStep sagaStep, String correlationId, String error) {
        sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, error);
    }

    private InvoicePdfDocument requireDocument(UUID documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> {
                    log.error("InvoicePdfDocument not found for id={} — TX2 may have raced with compensation", documentId);
                    return new IllegalStateException("Expected invoice PDF document is absent — internal state error");
                });
    }

    private void applyRetryCount(InvoicePdfDocument doc, int previousRetryCount) {
        if (previousRetryCount < 0) return;
        doc.incrementRetryCountTo(previousRetryCount + 1);
    }

    private InvoicePdfGeneratedEvent buildGeneratedEvent(InvoicePdfDocument doc,
                                                          String documentId, String documentNumber,
                                                          String sagaId, String correlationId) {
        return new InvoicePdfGeneratedEvent(
                sagaId,
                documentId,
                doc.getInvoiceNumber(),
                doc.getDocumentUrl(),
                doc.getFileSize(),
                doc.isXmlEmbedded(),
                correlationId);
    }
}
```

**Note:** There is a naming conflict in `completeGenerationAndPublish` — the parameter `documentId` shadows the method parameter `documentId`. Fix by renaming one:

```java
    @Transactional
    public void completeGenerationAndPublish(UUID documentId, String s3Key, String fileUrl,
                                             long fileSize, int previousRetryCount,
                                             String cmdDocumentId, String cmdDocumentNumber,
                                             String sagaId, SagaStep sagaStep, String correlationId) {
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Should compile now (or very few errors)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/application/service/InvoicePdfDocumentService.java
git commit -m "refactor: update InvoicePdfDocumentService method signatures to use plain fields instead of command objects"
```

---

## Task 8: Inline `InvoicePdfReplyEvent` factory in `SagaReplyPublisher`

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/messaging/SagaReplyPublisher.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/domain/event/InvoicePdfReplyEvent.java` (later in Task 9)

`InvoicePdfReplyEvent` will be replaced with static factory methods inside `SagaReplyPublisher`. The `SagaReplyPort` interface already defines `publishSuccess`, `publishFailure`, and `publishCompensated` with plain parameters — those stay the same.

- [ ] **Step 1: Rewrite SagaReplyPublisher to inline the reply event factory**

Remove the `InvoicePdfReplyEvent` import and create local static factory methods. The `ReplyStatus` enum and `SagaReply` class are from `com.wpanther.saga.domain.enums` and `com.wpanther.saga.domain.model`.

```java
package com.wpanther.invoice.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.invoice.pdf.application.port.out.SagaReplyPort;
import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@Slf4j
public class SagaReplyPublisher implements SagaReplyPort {

    private static final String AGGREGATE_TYPE = "InvoicePdfDocument";

    private final String replyTopic;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public SagaReplyPublisher(@Value("${app.kafka.topics.saga-reply-invoice-pdf}") String replyTopic,
                              OutboxService outboxService,
                              ObjectMapper objectMapper) {
        this.replyTopic = replyTopic;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId, String pdfUrl, long pdfSize) {
        InvoicePdfReplyEvent reply = InvoicePdfReplyEvent.success(sagaId, sagaStep, correlationId, pdfUrl, pdfSize);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "SUCCESS"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                replyTopic,
                sagaId,
                MessagingUtils.toJson(headers, objectMapper)
        );

        log.info("Published SUCCESS saga reply for saga {} step {} with pdfUrl={}", sagaId, sagaStep, pdfUrl);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        InvoicePdfReplyEvent reply = InvoicePdfReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "FAILURE"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                replyTopic,
                sagaId,
                MessagingUtils.toJson(headers, objectMapper)
        );

        log.info("Published FAILURE saga reply for saga {} step {}: {}", sagaId, sagaStep, errorMessage);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        InvoicePdfReplyEvent reply = InvoicePdfReplyEvent.compensated(sagaId, sagaStep, correlationId);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "COMPENSATED"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                replyTopic,
                sagaId,
                MessagingUtils.toJson(headers, objectMapper)
        );

        log.info("Published COMPENSATED saga reply for saga {} step {}", sagaId, sagaStep);
    }

    // Inline factory — replaces domain/event/InvoicePdfReplyEvent
    private static class InvoicePdfReplyEvent extends SagaReply {
        private static final long serialVersionUID = 1L;
        private final String pdfUrl;
        private final long pdfSize;

        private InvoicePdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status,
                                      String pdfUrl, long pdfSize) {
            super(sagaId, sagaStep, correlationId, status);
            this.pdfUrl = pdfUrl;
            this.pdfSize = pdfSize;
        }

        private InvoicePdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status) {
            super(sagaId, sagaStep, correlationId, status);
            this.pdfUrl = null;
            this.pdfSize = 0L;
        }

        private InvoicePdfReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
            super(sagaId, sagaStep, correlationId, errorMessage);
            this.pdfUrl = null;
            this.pdfSize = 0L;
        }

        public static InvoicePdfReplyEvent success(String sagaId, SagaStep sagaStep, String correlationId,
                                                    String pdfUrl, long pdfSize) {
            return new InvoicePdfReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS, pdfUrl, pdfSize);
        }

        public static InvoicePdfReplyEvent failure(String sagaId, SagaStep sagaStep, String correlationId,
                                                   String errorMessage) {
            return new InvoicePdfReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
        }

        public static InvoicePdfReplyEvent compensated(String sagaId, SagaStep sagaStep, String correlationId) {
            return new InvoicePdfReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Should compile cleanly

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/out/messaging/SagaReplyPublisher.java
git commit -m "refactor: inline InvoicePdfReplyEvent factory in SagaReplyPublisher"
```

---

## Task 9: Delete old files from `domain/event/` and `infrastructure/adapter/in/kafka/`

**Files:**
- Delete: `src/main/java/com/wpanther/invoice/pdf/domain/event/ProcessInvoicePdfCommand.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/domain/event/CompensateInvoicePdfCommand.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/domain/event/InvoicePdfGeneratedEvent.java` (original location)
- Delete: `src/main/java/com/wpanther/invoice/pdf/domain/event/InvoicePdfReplyEvent.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaProcessInvoicePdfCommand.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaCompensateInvoicePdfCommand.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapper.java`

- [ ] **Step 1: Delete all old files**

```bash
rm src/main/java/com/wpanther/invoice/pdf/domain/event/ProcessInvoicePdfCommand.java
rm src/main/java/com/wpanther/invoice/pdf/domain/event/CompensateInvoicePdfCommand.java
rm src/main/java/com/wpanther/invoice/pdf/domain/event/InvoicePdfGeneratedEvent.java
rm src/main/java/com/wpanther/invoice/pdf/domain/event/InvoicePdfReplyEvent.java
rm src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaProcessInvoicePdfCommand.java
rm src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaCompensateInvoicePdfCommand.java
rm src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/KafkaCommandMapper.java
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Clean compile

- [ ] **Step 3: Commit**

```bash
git add -A  # stage deletions
git commit -m "refactor: delete old saga command classes from domain/event/ and Kafka prefix files"
```

---

## Task 10: Delete `application/service/SagaCommandHandler` (original location) and `application/usecase/`

**Files:**
- Delete: `src/main/java/com/wpanther/invoice/pdf/application/service/SagaCommandHandler.java` (original location)
- Delete: `src/main/java/com/wpanther/invoice/pdf/application/usecase/ProcessInvoicePdfUseCase.java`
- Delete: `src/main/java/com/wpanther/invoice/pdf/application/usecase/CompensateInvoicePdfUseCase.java`

```bash
rm src/main/java/com/wpanther/invoice/pdf/application/service/SagaCommandHandler.java
rm src/main/java/com/wpanther/invoice/pdf/application/usecase/ProcessInvoicePdfUseCase.java
rm src/main/java/com/wpanther/invoice/pdf/application/usecase/CompensateInvoicePdfUseCase.java
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Clean compile

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: delete original SagaCommandHandler and old usecase interfaces"
```

---

## Task 11: Update `SagaRouteConfig` — remove `KafkaCommandMapper`, use new DTO names

**Files:**
- Modify: `src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/SagaRouteConfig.java`

Key changes:
1. Remove `KafkaCommandMapper` field and constructor parameter
2. Change `commandMapper.toProcess(cmd)` calls to direct DTO usage
3. The route processors now call `processUseCase.handle(docId, docNumber, signedXmlUrl, sagaId, step, corrId)` directly using DTO getters
4. `onPrepareFailure` block uses `ProcessInvoicePdfCommand` / `CompensateInvoicePdfCommand` (from `dto/` package)

- [ ] **Step 1: Rewrite SagaRouteConfig**

```java
package com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.invoice.pdf.application.port.in.CompensateInvoicePdfUseCase;
import com.wpanther.invoice.pdf.application.port.in.ProcessInvoicePdfUseCase;
import com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka.dto.CompensateInvoicePdfCommand;
import com.wpanther.invoice.pdf.infrastructure.adapter.in.kafka.dto.ProcessInvoicePdfCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final ProcessInvoicePdfUseCase processUseCase;
    private final CompensateInvoicePdfUseCase compensateUseCase;
    private final SagaCommandHandler sagaCommandHandler;
    private final ObjectMapper objectMapper;

    public SagaRouteConfig(ProcessInvoicePdfUseCase processUseCase,
                           CompensateInvoicePdfUseCase compensateUseCase,
                           SagaCommandHandler sagaCommandHandler,
                           ObjectMapper objectMapper) {
        this.processUseCase = processUseCase;
        this.compensateUseCase = compensateUseCase;
        this.sagaCommandHandler = sagaCommandHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() throws Exception {

        errorHandler(deadLetterChannel(
                        "kafka:{{app.kafka.topics.dlq}}?brokers={{app.kafka.bootstrap-servers}}")
                        .maximumRedeliveries(3)
                        .redeliveryDelay(1000)
                        .useExponentialBackOff()
                        .backOffMultiplier(2)
                        .maximumRedeliveryDelay(10000)
                        .logExhausted(true)
                        .logStackTrace(true)
                        .onPrepareFailure(exchange -> {
                            Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                            Object body = exchange.getIn().getBody();
                            if (body instanceof ProcessInvoicePdfCommand cmd) {
                                log.error("DLQ: notifying orchestrator of retry exhaustion for saga {} document {}",
                                        cmd.getSagaId(), cmd.getDocumentNumber());
                                sagaCommandHandler.publishOrchestrationFailure(
                                        cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId(), cause);
                            } else if (body instanceof CompensateInvoicePdfCommand cmd) {
                                log.error("DLQ: notifying orchestrator of compensation retry exhaustion for saga {} document {}",
                                        cmd.getSagaId(), cmd.getDocumentId());
                                sagaCommandHandler.publishCompensationOrchestrationFailure(
                                        cmd.getSagaId(), cmd.getSagaStep(), cmd.getCorrelationId(), cause);
                            } else {
                                log.error("DLQ: body not deserialized ({}); attempting saga metadata recovery",
                                        body == null ? "null" : body.getClass().getSimpleName());
                                recoverAndNotifyOrchestrator(body, cause);
                            }
                        }));

        from("kafka:{{app.kafka.topics.saga-command-invoice-pdf}}"
                        + "?brokers={{app.kafka.bootstrap-servers}}"
                        + "&groupId={{app.kafka.consumer.command-group-id}}"
                        + "&autoOffsetReset=earliest"
                        + "&autoCommitEnable=false"
                        + "&breakOnFirstError={{app.kafka.consumer.break-on-first-error}}"
                        + "&maxPollRecords={{app.kafka.consumer.max-poll-records}}"
                        + "&consumersCount={{app.kafka.consumer.consumers-count}}")
                        .routeId("saga-command-consumer")
                        .log(LoggingLevel.DEBUG, "Received saga command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                        .unmarshal().json(JsonLibrary.Jackson, ProcessInvoicePdfCommand.class)
                        .process(exchange -> {
                                ProcessInvoicePdfCommand cmd = exchange.getIn().getBody(ProcessInvoicePdfCommand.class);
                                log.info("Processing saga command for saga: {}, document: {}",
                                                cmd.getSagaId(), cmd.getDocumentNumber());
                                processUseCase.handle(
                                        cmd.getDocumentId(),
                                        cmd.getDocumentNumber(),
                                        cmd.getSignedXmlUrl(),
                                        cmd.getSagaId(),
                                        cmd.getSagaStep(),
                                        cmd.getCorrelationId());
                        })
                        .log("Successfully processed saga command");

        from("kafka:{{app.kafka.topics.saga-compensation-invoice-pdf}}"
                        + "?brokers={{app.kafka.bootstrap-servers}}"
                        + "&groupId={{app.kafka.consumer.compensation-group-id}}"
                        + "&autoOffsetReset=earliest"
                        + "&autoCommitEnable=false"
                        + "&breakOnFirstError={{app.kafka.consumer.break-on-first-error}}"
                        + "&maxPollRecords={{app.kafka.consumer.max-poll-records}}"
                        + "&consumersCount={{app.kafka.consumer.consumers-count}}")
                        .routeId("saga-compensation-consumer")
                        .log(LoggingLevel.DEBUG, "Received compensation command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                        .unmarshal().json(JsonLibrary.Jackson, CompensateInvoicePdfCommand.class)
                        .process(exchange -> {
                                CompensateInvoicePdfCommand cmd = exchange.getIn().getBody(CompensateInvoicePdfCommand.class);
                                log.info("Processing compensation for saga: {}, document: {}",
                                                cmd.getSagaId(), cmd.getDocumentId());
                                compensateUseCase.handle(
                                        cmd.getDocumentId(),
                                        cmd.getSagaId(),
                                        cmd.getSagaStep(),
                                        cmd.getCorrelationId());
                        })
                        .log("Successfully processed compensation command");
    }

    private void recoverAndNotifyOrchestrator(Object body, Throwable cause) {
        if (body == null) {
            log.error("DLQ: null message body — orchestrator must timeout");
            return;
        }
        try {
            byte[] rawBytes = body instanceof byte[] b
                    ? b
                    : body.toString().getBytes(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(rawBytes);
            String sagaId = node.path("sagaId").asText(null);
            String sagaStepStr = node.path("sagaStep").asText(null);
            String correlationId = node.path("correlationId").asText(null);

            if (sagaId == null || sagaStepStr == null) {
                log.error("DLQ: saga metadata missing in raw message — orchestrator must timeout");
                return;
            }
            SagaStep sagaStep = objectMapper.readValue("\"" + sagaStepStr + "\"", SagaStep.class);
            sagaCommandHandler.publishOrchestrationFailureForUnparsedMessage(sagaId, sagaStep, correlationId, cause);
        } catch (Exception parseEx) {
            log.error("DLQ: cannot parse raw message for saga metadata — orchestrator must timeout", parseEx);
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q 2>&1 | head -40`
Expected: Clean compile

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/SagaRouteConfig.java
git commit -m "refactor: update SagaRouteConfig to use new DTO package and remove KafkaCommandMapper"
```

---

## Task 12: Build and test

**Files:**
- All modified files

- [ ] **Step 1: Full clean compile**

Run: `mvn clean compile 2>&1 | tail -20`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run tests**

Run: `mvn clean test 2>&1 | tail -30`
Expected: BUILD SUCCESS (all tests pass)

- [ ] **Step 3: Commit all remaining changes**

```bash
git add -A
git commit -m "refactor: complete layer separation — saga types in infrastructure, plain-parameter use cases in application/port/in/"
```

---

## Verification Checklist

After all tasks:

```bash
# 1. Compile check
mvn clean compile -q

# 2. All tests pass
mvn clean test -q

# 3. Confirm domain/event is empty (only pure domain events should remain, if any)
ls src/main/java/com/wpanther/invoice/pdf/domain/event/

# Expected output: empty directory OR only truly domain-specific files (no SagaCommand, SagaReply, TraceEvent, Jackson)

# 4. Confirm new structure
ls src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/dto/
# Expected: CompensateInvoicePdfCommand.java, ProcessInvoicePdfCommand.java

ls src/main/java/com/wpanther/invoice/pdf/application/port/in/
# Expected: CompensateInvoicePdfUseCase.java, ProcessInvoicePdfUseCase.java

ls src/main/java/com/wpanther/invoice/pdf/application/dto/event/
# Expected: InvoicePdfGeneratedEvent.java

ls src/main/java/com/wpanther/invoice/pdf/infrastructure/adapter/in/kafka/
# Expected: SagaCommandHandler.java, SagaRouteConfig.java (NO KafkaCommandMapper)
```

---

## Self-Review Checklist

- [ ] `domain/event/` is now empty of saga types (no `extends SagaCommand`, `SagaReply`, `TraceEvent`)
- [ ] `ProcessInvoicePdfCommand` and `CompensateInvoicePdfCommand` are in `infrastructure/adapter/in/kafka/dto/`
- [ ] Use case interfaces in `application/port/in/` have plain parameter signatures (no command objects)
- [ ] `SagaCommandHandler` is in `infrastructure/adapter/in/kafka/` and calls use cases with extracted fields
- [ ] `InvoicePdfGeneratedEvent` is in `application/dto/event/`
- [ ] `InvoicePdfReplyEvent` is inlined in `SagaReplyPublisher`
- [ ] `KafkaCommandMapper` is deleted
- [ ] All tests pass with `mvn clean test`
- [ ] Compilation succeeds with `mvn clean compile`