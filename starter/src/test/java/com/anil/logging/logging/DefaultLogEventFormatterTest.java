package com.anil.logging.logging;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.masking.JsonSensitiveDataMasker;
import com.anil.logging.model.HttpLogEvent;
import com.anil.logging.model.LogCategory;
import com.anil.logging.model.LogEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLogEventFormatterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void rendersJsonWithSnakeCaseFields() throws Exception {
        LoggingProperties properties = new LoggingProperties();
        DefaultLogEventFormatter formatter = new DefaultLogEventFormatter(
                properties,
                objectMapper,
                new JsonSensitiveDataMasker(properties, objectMapper),
                "order-service",
                "test");
        LogEvent event = new LogEvent();
        event.setType(LogCategory.BUSINESS);
        event.setMessage("Order created");
        event.field("orderId", "1001");
        event.field("durationMs", 42);
        event.field("already_snake", "ok");

        JsonNode json = objectMapper.readTree(formatter.format(event));

        assertThat(json.get("order_id").asText()).isEqualTo("1001");
        assertThat(json.get("duration_ms").asInt()).isEqualTo(42);
        assertThat(json.get("already_snake").asText()).isEqualTo("ok");
        assertThat(json.has("orderId")).isFalse();
        assertThat(json.has("durationMs")).isFalse();
    }

    @Test
    void rendersJsonPayloadObjectsAndArraysAsJsonNotEscapedStrings() throws Exception {
        LoggingProperties properties = new LoggingProperties();
        DefaultLogEventFormatter formatter = new DefaultLogEventFormatter(
                properties,
                objectMapper,
                new JsonSensitiveDataMasker(properties, objectMapper),
                "order-service",
                "test");
        HttpLogEvent event = new HttpLogEvent();
        event.setType(LogCategory.HTTP_RESPONSE);
        event.setMessage("HTTP response POST /api/orders 201");
        event.requestPayload("{\"order_id\":\"1001\",\"amount\":500}");
        event.responsePayload("[{\"status\":\"CREATED\"},{\"status\":\"QUEUED\"}]");

        JsonNode json = objectMapper.readTree(formatter.format(event));

        assertThat(json.get("request_payload").isObject()).isTrue();
        assertThat(json.get("request_payload").get("order_id").asText()).isEqualTo("1001");
        assertThat(json.get("response_payload").isArray()).isTrue();
        assertThat(json.get("response_payload").get(0).get("status").asText()).isEqualTo("CREATED");
    }

    @Test
    void leavesInvalidPayloadAsString() throws Exception {
        LoggingProperties properties = new LoggingProperties();
        DefaultLogEventFormatter formatter = new DefaultLogEventFormatter(
                properties,
                objectMapper,
                new JsonSensitiveDataMasker(properties, objectMapper),
                "order-service",
                "test");
        HttpLogEvent event = new HttpLogEvent();
        event.setType(LogCategory.HTTP_RESPONSE);
        event.setMessage("HTTP response POST /api/orders 201");
        event.responsePayload("plain text");

        JsonNode json = objectMapper.readTree(formatter.format(event));

        assertThat(json.get("response_payload").isTextual()).isTrue();
        assertThat(json.get("response_payload").asText()).isEqualTo("plain text");
    }

    @Test
    void masksNestedFieldsAndEveryExceptionMessageInStackTrace() throws Exception {
        LoggingProperties properties = new LoggingProperties();
        DefaultLogEventFormatter formatter = new DefaultLogEventFormatter(
                properties, objectMapper, new JsonSensitiveDataMasker(properties, objectMapper),
                "order-service", "test");
        LogEvent event = new LogEvent();
        event.setMessage("failed");
        event.field("customer", Map.of("password", "NESTED_SECRET"));
        event.setThrowable(new IllegalStateException("token=TOP_SECRET",
                new IllegalArgumentException("password=CAUSE_SECRET")));

        String formatted = formatter.format(event);
        JsonNode json = objectMapper.readTree(formatted);

        assertThat(formatted).doesNotContain("NESTED_SECRET", "TOP_SECRET", "CAUSE_SECRET");
        assertThat(json.at("/customer/password").asText()).isEqualTo("***");
        assertThat(json.at("/exception/stack_trace").asText()).contains("token=***", "password=***");
    }

    @Test
    void applicationFieldsCannotOverrideReservedMetadata() throws Exception {
        LoggingProperties properties = new LoggingProperties();
        DefaultLogEventFormatter formatter = new DefaultLogEventFormatter(
                properties, objectMapper, new JsonSensitiveDataMasker(properties, objectMapper),
                "order-service", "test");
        LogEvent event = new LogEvent();
        event.setLevel("INFO");
        event.setMessage("trusted message");
        event.field("level", "ERROR");
        event.field("message", "spoofed");
        event.field("service", "spoofed-service");

        JsonNode json = objectMapper.readTree(formatter.format(event));

        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("message").asText()).isEqualTo("trusted message");
        assertThat(json.get("service").asText()).isEqualTo("order-service");
    }

    @Test
    void httpIncludeFlagsSuppressTraceAndUserMdcFields() throws Exception {
        LoggingProperties properties = new LoggingProperties();
        properties.getInclude().setTrace(false);
        properties.getInclude().setUser(false);
        MDC.put("trace_id", "trace-secret");
        MDC.put("user_id", "user-secret");
        DefaultLogEventFormatter formatter = new DefaultLogEventFormatter(
                properties, objectMapper, new JsonSensitiveDataMasker(properties, objectMapper),
                "order-service", "test");
        HttpLogEvent event = new HttpLogEvent();
        event.setType(LogCategory.HTTP_RESPONSE);
        event.setMessage("response");

        JsonNode json = objectMapper.readTree(formatter.format(event));

        assertThat(json.has("trace_id")).isFalse();
        assertThat(json.has("user_id")).isFalse();
    }
}
