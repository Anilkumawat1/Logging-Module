package com.anil.logging.logging;

import com.anil.logging.model.LogCategory;

import java.util.Map;

public class AuditLogger {
    private final LoggingService loggingService;

    public AuditLogger(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public void log(String action, Map<String, ?> fields) {
        java.util.Map<String, Object> eventFields = new java.util.LinkedHashMap<>();
        eventFields.put("operation", action);
        if (fields != null) {
            eventFields.putAll(fields);
        }
        loggingService.info(LogCategory.AUDIT, action, eventFields);
    }
}
