package com.anil.logging.logging;

import com.anil.logging.model.LogCategory;

import java.util.Map;

public interface LoggingService {
    void info(LogCategory category, String message, Map<String, ?> fields);

    void warn(LogCategory category, String message, Map<String, ?> fields);

    void error(LogCategory category, String message, Map<String, ?> fields, Throwable throwable);

    FluentLogEvent info(String message);

    TimerContext timer(String operation);
}
