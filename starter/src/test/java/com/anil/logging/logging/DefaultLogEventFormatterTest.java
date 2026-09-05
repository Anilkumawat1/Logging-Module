package com.anil.logging.logging;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.masking.JsonSensitiveDataMasker;
import com.anil.logging.model.LogCategory;
import com.anil.logging.model.LogEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLogEventFormatterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

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
}
