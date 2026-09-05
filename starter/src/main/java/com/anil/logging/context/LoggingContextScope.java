package com.anil.logging.context;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LoggingContextScope implements AutoCloseable {
    private final Map<String, String> previous;
    private boolean closed;

    LoggingContextScope(Map<String, String> fields) {
        this.previous = MDC.getCopyOfContextMap();
        fields.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }

    static LoggingContextScope of(String key, Object value) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(key, value == null ? null : String.valueOf(value));
        return new LoggingContextScope(fields);
    }

    static LoggingContextScope of(Map<String, ?> values) {
        Map<String, String> fields = new LinkedHashMap<>();
        values.forEach((key, value) -> fields.put(key, value == null ? null : String.valueOf(value)));
        return new LoggingContextScope(fields);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (previous == null || previous.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
}
