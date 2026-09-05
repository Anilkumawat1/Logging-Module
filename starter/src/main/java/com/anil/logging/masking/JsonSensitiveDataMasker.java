package com.anil.logging.masking;

import com.anil.logging.config.LoggingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JsonSensitiveDataMasker implements SensitiveDataMasker {
    private final LoggingProperties properties;
    private final ObjectMapper objectMapper;
    private final Set<String> fields;
    private final Set<String> headers;
    private final Set<String> queryParameters;

    public JsonSensitiveDataMasker(LoggingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fields = normalized(properties.getMasking().getFields());
        this.headers = normalized(properties.getMasking().getHeaders());
        this.queryParameters = normalized(properties.getMasking().getQueryParameters());
    }

    @Override
    public Object maskValue(String fieldName, Object value) {
        if (!properties.getMasking().isEnabled()) {
            return value;
        }
        return isSensitive(fieldName, MaskingTarget.FIELD) ? properties.getMasking().getReplacement() : value;
    }

    @Override
    public String maskPayload(String payload) {
        if (payload == null || payload.isBlank() || !properties.getMasking().isEnabled()) {
            return payload;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            maskNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (RuntimeException | JsonProcessingException ex) {
            return maskText(payload);
        }
    }

    @Override
    public Map<String, ?> maskMap(Map<String, ?> source, MaskingTarget target) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        source.forEach((key, value) -> masked.put(key, isSensitive(key, target) ? properties.getMasking().getReplacement() : value));
        return masked;
    }

    private void maskNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                if (isSensitive(entry.getKey(), MaskingTarget.FIELD)) {
                    objectNode.put(entry.getKey(), properties.getMasking().getReplacement());
                } else {
                    maskNode(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskNode);
        }
    }

    private String maskText(String payload) {
        String result = payload;
        for (String field : fields) {
            String quoted = Pattern.quote(field);
            result = result.replaceAll("(?i)(\"" + quoted + "\"\\s*:\\s*\")[^\"]*(\")", "$1" + properties.getMasking().getReplacement() + "$2");
            result = result.replaceAll("(?i)(" + quoted + "\\s*=\\s*)[^&\\s,}]+", "$1" + properties.getMasking().getReplacement());
        }
        return result;
    }

    private boolean isSensitive(String key, MaskingTarget target) {
        String normalized = normalize(key);
        return switch (target) {
            case HEADER -> headers.contains(normalized) || containsSecretWord(normalized);
            case QUERY_PARAMETER -> queryParameters.contains(normalized);
            case FIELD -> fields.contains(normalized);
        };
    }

    private static boolean containsSecretWord(String normalized) {
        return normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.contains("api-key")
                || normalized.contains("apikey")
                || normalized.contains("cookie");
    }

    private static Set<String> normalized(Iterable<String> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(JsonSensitiveDataMasker::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
