package com.anil.logging.logging;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.model.LogCategory;
import com.anil.logging.model.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultLoggingService implements LoggingService {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.anil.logging.structured");
    private final LoggingProperties properties;
    private final LogEventFormatter formatter;

    public DefaultLoggingService(LoggingProperties properties, LogEventFormatter formatter) {
        this.properties = properties;
        this.formatter = formatter;
    }

    @Override
    public void info(LogCategory category, String message, Map<String, ?> fields) {
        log("INFO", category, message, fields, null);
    }

    @Override
    public void warn(LogCategory category, String message, Map<String, ?> fields) {
        log("WARN", category, message, fields, null);
    }

    @Override
    public void error(LogCategory category, String message, Map<String, ?> fields, Throwable throwable) {
        log("ERROR", category, message, fields, throwable);
    }

    @Override
    public FluentLogEvent info(String message) {
        return new DefaultFluentLogEvent(this, "INFO", LogCategory.APPLICATION, message);
    }

    @Override
    public TimerContext timer(String operation) {
        long start = System.nanoTime();
        return () -> info(LogCategory.PERFORMANCE, operation, Map.of(
                "operation", operation,
                "duration_ms", (System.nanoTime() - start) / 1_000_000));
    }

    void log(String level, LogCategory category, String message, Map<String, ?> fields, Throwable throwable) {
        if (!properties.categoryEnabled(category)) {
            return;
        }
        try {
            LogEvent event = new LogEvent();
            event.setLevel(level);
            event.setType(category);
            event.setMessage(message);
            event.setThrowable(throwable);
            event.field("logger", LOGGER.getName());
            if (fields != null) {
                fields.forEach(event::field);
            }
            String formatted = formatter.format(event);
            if ("ERROR".equalsIgnoreCase(level)) {
                LOGGER.error(formatted);
            } else if ("WARN".equalsIgnoreCase(level)) {
                LOGGER.warn(formatted);
            } else {
                LOGGER.info(formatted);
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("structured logging failed: {}", ex.toString());
        }
    }

    private static class DefaultFluentLogEvent implements FluentLogEvent {
        private final DefaultLoggingService service;
        private final String level;
        private LogCategory category;
        private final String message;
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private DefaultFluentLogEvent(DefaultLoggingService service, String level, LogCategory category, String message) {
            this.service = service;
            this.level = level;
            this.category = category;
            this.message = message;
        }

        @Override
        public FluentLogEvent category(LogCategory category) {
            this.category = category;
            return this;
        }

        @Override
        public FluentLogEvent field(String key, Object value) {
            fields.put(key, value);
            return this;
        }

        @Override
        public void log() {
            service.log(level, category, message, fields, null);
        }
    }
}
