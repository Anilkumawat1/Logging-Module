package com.anil.logging.context;

import org.slf4j.MDC;

import java.util.Map;

public final class LoggingContext {
    private LoggingContext() {
    }

    public static String get(String key) {
        return MDC.get(key);
    }

    public static void put(String key, Object value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, String.valueOf(value));
        }
    }

    public static void remove(String key) {
        MDC.remove(key);
    }

    public static void clear() {
        MDC.clear();
    }

    public static Map<String, String> copy() {
        Map<String, String> copy = MDC.getCopyOfContextMap();
        return copy == null ? Map.of() : Map.copyOf(copy);
    }

    public static LoggingContextScope with(String key, Object value) {
        return LoggingContextScope.of(key, value);
    }

    public static LoggingContextScope with(Map<String, ?> values) {
        return LoggingContextScope.of(values);
    }
}
