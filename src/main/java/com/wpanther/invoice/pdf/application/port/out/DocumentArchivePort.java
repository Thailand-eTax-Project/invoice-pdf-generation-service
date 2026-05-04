package com.wpanther.invoice.pdf.application.port.out;

import com.wpanther.invoice.pdf.application.dto.event.DocumentArchiveEvent;

public interface DocumentArchivePort {
    void publish(DocumentArchiveEvent event);
}
