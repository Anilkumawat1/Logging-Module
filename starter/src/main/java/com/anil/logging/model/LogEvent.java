package com.anil.logging.model;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class LogEvent {
    private OffsetDateTime timestamp = OffsetDateTime.now();
    private String level = "INFO";
    private LogCategory type = LogCategory.APPLICATION;
    private String message;
    private Throwable throwable;
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public LogCategory getType() {
        return type;
    }

    public void setType(LogCategory type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public LogEvent field(String key, Object value) {
        if (value != null) {
            fields.put(key, value);
        }
        return this;
    }
}
