package com.wpanther.invoice.pdf.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.invoice.pdf.application.dto.event.DocumentArchiveEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ArchiveBeforeUploadFailsTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OutboxDocumentArchiveAdapter adapter = new OutboxDocumentArchiveAdapter(null, mapper);

    @Test
    void blocksNullSourceUrl() {
        DocumentArchiveEvent event = new DocumentArchiveEvent(
                "doc-1", "INV-1", "INVOICE", "UNSIGNED_PDF",
                null, "x.pdf", "application/pdf", 1L, "s", "c");

        assertThatThrownBy(() -> adapter.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to publish DocumentArchiveEvent with null/blank sourceUrl");
    }

    @Test
    void blocksBlankSourceUrl() {
        DocumentArchiveEvent event = new DocumentArchiveEvent(
                "doc-1", "INV-1", "INVOICE", "UNSIGNED_PDF",
                "   ", "x.pdf", "application/pdf", 1L, "s", "c");

        assertThatThrownBy(() -> adapter.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to publish DocumentArchiveEvent with null/blank sourceUrl");
    }
}
