package com.anil.logging.operation;

import com.anil.logging.config.LoggingProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Locale;

public class DefaultOperationResolver implements OperationResolver {
    public static final String ATTRIBUTE = "com.anil.logging.operation";
    private final LoggingProperties properties;

    public DefaultOperationResolver(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        Object annotated = request.getAttribute(ATTRIBUTE);
        if (annotated instanceof String value && !value.isBlank()) {
            return value;
        }
        String endpoint = endpoint(request);
        String mapped = properties.getOperations().getMappings().get(request.getMethod() + " " + endpoint);
        if (mapped != null) {
            return mapped;
        }
        return automatic(request.getMethod(), endpoint);
    }

    private static String endpoint(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? request.getRequestURI() : String.valueOf(pattern);
    }

    private static String automatic(String method, String endpoint) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST" -> "CREATE";
            case "GET" -> "READ";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> endpoint;
        };
    }
}
