package com.anil.logging.logging;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.context.MdcKeys;
import com.anil.logging.masking.SensitiveDataMasker;
import com.anil.logging.model.LogEvent;
import com.anil.logging.model.LogFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultLogEventFormatter implements LogEventFormatter {
    private final LoggingProperties properties;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker masker;
    private final String serviceName;
    private final String environment;

    public DefaultLogEventFormatter(LoggingProperties properties, ObjectMapper objectMapper,
                                    SensitiveDataMasker masker, String serviceName, String environment) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.masker = masker;
        this.serviceName = serviceName;
        this.environment = environment;
    }

    @Override
    public String format(LogEvent event) {
        Map<String, Object> map = toMap(event);
        if (properties.getFormat() == LogFormat.TEXT) {
            return toText(map);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return toText(map);
        }
    }

    private Map<String, Object> toMap(LogEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(event.getTimestamp()));
        map.put("level", event.getLevel());
        map.put("type", event.getType());
        putIfEnabled(map, "service", serviceName, properties.getContext().getFields().isService());
        putIfEnabled(map, "environment", environment, properties.getContext().getFields().isEnvironment());
        putMdc(map, MdcKeys.REQUEST_ID, properties.getContext().getFields().isRequestId());
        putMdc(map, MdcKeys.TRACE_ID, properties.getContext().getFields().isTraceId());
        putMdc(map, MdcKeys.SPAN_ID, properties.getContext().getFields().isSpanId());
        putMdc(map, MdcKeys.USER_ID, properties.getContext().getFields().isUserId());
        putMdc(map, MdcKeys.ROLES, properties.getContext().getFields().isRoles());
        if (properties.getInclude().isThread()) {
            map.put("thread", Thread.currentThread().getName());
        }
        map.put("message", masker.maskPayload(event.getMessage()));
        event.getFields().forEach((key, value) -> map.put(toSnakeCase(key), masker.maskValue(key, value)));
        if (event.getThrowable() != null) {
            map.put("exception", exceptionMap(event.getThrowable()));
        }
        return map;
    }

    private void putMdc(Map<String, Object> map, String key, boolean enabled) {
        putIfEnabled(map, key, MDC.get(key), enabled);
    }

    private static void putIfEnabled(Map<String, Object> map, String key, Object value, boolean enabled) {
        if (enabled && value != null) {
            map.putIfAbsent(key, value);
        }
    }

    private Map<String, Object> exceptionMap(Throwable throwable) {
        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("type", throwable.getClass().getName());
        exception.put("message", masker.maskPayload(truncate(throwable.getMessage(), 2_000)));
        exception.put("stack_trace", stackTrace(throwable));
        return exception;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...[truncated]";
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return truncate(writer.toString(), 20_000);
    }

    private static String toText(Map<String, Object> map) {
        StringBuilder builder = new StringBuilder();
        builder.append(map.get("timestamp")).append(' ')
                .append(map.get("level")).append(' ')
                .append('[').append(map.get("type")).append(']');
        appendBracket(builder, "service", map);
        appendBracket(builder, "environment", map);
        appendBracket(builder, "request_id", map);
        appendBracket(builder, "user_id", map);
        appendBracket(builder, "thread", map);
        builder.append(' ').append(map.get("message"));
        map.forEach((key, value) -> {
            if (!coreField(key) && value != null) {
                builder.append(' ').append(key).append('=').append(value);
            }
        });
        return builder.toString();
    }

    private static void appendBracket(StringBuilder builder, String key, Map<String, Object> map) {
        Object value = map.get(key);
        if (value != null) {
            builder.append(" [").append(key).append('=').append(value).append(']');
        }
    }

    private static boolean coreField(String key) {
        return switch (key) {
            case "timestamp", "level", "type", "service", "environment", "request_id", "trace_id",
                    "span_id", "user_id", "role_ids", "thread", "message" -> true;
            default -> false;
        };
    }

    private static String toSnakeCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        char previous = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isUpperCase(current) && index > 0 && previous != '_' && previous != '-') {
                builder.append('_');
            } else if (current == '-') {
                builder.append('_');
                previous = '_';
                continue;
            }
            builder.append(Character.toLowerCase(current));
            previous = current;
        }
        return builder.toString();
    }
}
