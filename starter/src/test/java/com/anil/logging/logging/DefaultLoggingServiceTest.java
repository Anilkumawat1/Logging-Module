package com.anil.logging.logging;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.model.LogCategory;
import com.anil.logging.model.LogEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLoggingServiceTest {

    @Test
    void globalAndCategorySwitchesSuppressStructuredEvents() {
        LoggingProperties properties = new LoggingProperties();
        AtomicInteger formatted = new AtomicInteger();
        DefaultLoggingService service = new DefaultLoggingService(properties, event -> {
            formatted.incrementAndGet();
            return "event";
        });

        properties.setEnabled(false);
        service.info(LogCategory.BUSINESS, "disabled", Map.of());
        properties.setEnabled(true);
        properties.getCategories().put(LogCategory.BUSINESS, false);
        service.info(LogCategory.BUSINESS, "category disabled", Map.of());

        assertThat(formatted).hasValue(0);
    }

    @Test
    void loggerFieldHonorsIncludeLoggerProperty() {
        LoggingProperties properties = new LoggingProperties();
        properties.getInclude().setLogger(false);
        AtomicReference<LogEvent> captured = new AtomicReference<>();
        DefaultLoggingService service = new DefaultLoggingService(properties, event -> {
            captured.set(event);
            return "event";
        });

        service.info(LogCategory.BUSINESS, "created", Map.of("order_id", "1"));

        assertThat(captured.get().getFields()).containsEntry("order_id", "1").doesNotContainKey("logger");
    }
}
