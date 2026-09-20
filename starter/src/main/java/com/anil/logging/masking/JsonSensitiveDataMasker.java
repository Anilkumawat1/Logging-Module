package com.anil.logging.masking;

import com.anil.logging.config.LoggingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Matcher;
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
        if (isSensitive(fieldName, MaskingTarget.FIELD)) {
            return properties.getMasking().getReplacement();
        }
        return maskNested(value, new IdentityHashMap<>(), 0);
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
        if (!properties.getMasking().isEnabled()) {
            return new LinkedHashMap<>(source);
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        source.forEach((key, value) -> masked.put(key,
                isSensitive(key, target)
                        ? properties.getMasking().getReplacement()
                        : maskNested(value, new IdentityHashMap<>(), 0)));
        return masked;
    }

    private Object maskNested(Object value, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (value == null || isScalar(value) || depth >= 32) {
            return value;
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            return "[cyclic]";
        }
        try {
            if (value instanceof JsonNode node) {
                JsonNode copy = node.deepCopy();
                maskNode(copy);
                return copy;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, nestedValue) -> {
                    String nestedKey = String.valueOf(key);
                    result.put(nestedKey, isSensitive(nestedKey, MaskingTarget.FIELD)
                            ? properties.getMasking().getReplacement()
                            : maskNested(nestedValue, visited, depth + 1));
                });
                return result;
            }
            if (value instanceof Collection<?> collection) {
                List<Object> result = new ArrayList<>(collection.size());
                collection.forEach(item -> result.add(maskNested(item, visited, depth + 1)));
                return result;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<Object> result = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    result.add(maskNested(Array.get(value, index), visited, depth + 1));
                }
                return result;
            }
            return value;
        } finally {
            visited.remove(value);
        }
    }

    private static boolean isScalar(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>;
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
        String replacement = Matcher.quoteReplacement(properties.getMasking().getReplacement());
        for (String field : fields) {
            String quoted = Pattern.quote(field);
            result = result.replaceAll("(?i)(\"" + quoted + "\"\\s*:\\s*\")[^\"]*(\")", "$1" + replacement + "$2");
            result = result.replaceAll("(?i)(\"" + quoted + "\"\\s*:\\s*)(?!\")([^,}\\s]+)", "$1" + replacement);
            result = result.replaceAll("(?i)(" + quoted + "\\s*=\\s*)[^&\\s,}]+", "$1" + replacement);
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
