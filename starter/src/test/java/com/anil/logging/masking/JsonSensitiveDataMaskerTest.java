package com.anil.logging.masking;

import com.anil.logging.config.LoggingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSensitiveDataMaskerTest {
    private final JsonSensitiveDataMasker masker = new JsonSensitiveDataMasker(new LoggingProperties(), new ObjectMapper());

    @Test
    void masksNestedJsonObjectsAndArraysCaseInsensitively() {
        String masked = masker.maskPayload("""
                {"user":"a","Password":"secret","items":[{"cardNumber":"4111","cvv":"123"}]}
                """);

        assertThat(masked).doesNotContain("secret", "4111", "123")
                .contains("\"Password\":\"***\"", "\"cardNumber\":\"***\"", "\"cvv\":\"***\"");
    }

    @Test
    void masksHeadersAndQueryParametersCaseInsensitively() {
        Map<String, Object> headers = (Map<String, Object>) masker.maskMap(Map.of("Authorization", "Bearer SECRET", "postman-token", "SECRET", "X-Normal", "ok"), MaskingTarget.HEADER);
        Map<String, Object> params = (Map<String, Object>) masker.maskMap(Map.of("AccessToken", "SECRET", "page", "1"), MaskingTarget.QUERY_PARAMETER);

        assertThat(headers).containsEntry("Authorization", "***").containsEntry("postman-token", "***").containsEntry("X-Normal", "ok");
        assertThat(params).containsEntry("AccessToken", "***").containsEntry("page", "1");
    }

    @Test
    void masksInvalidJsonAsTextWithoutThrowing() {
        String masked = masker.maskPayload("password=secret token=abc123");

        assertThat(masked).doesNotContain("secret", "abc123").contains("password=***", "token=***");
    }

    @Test
    void recursivelyMasksNestedStructuredValues() {
        Object masked = masker.maskValue("customer", Map.of(
                "name", "Anil",
                "credentials", Map.of("password", "SECRET"),
                "cards", List.of(Map.of("cvv", "123"))));

        assertThat(masked.toString()).doesNotContain("SECRET", "123")
                .contains("password=***", "cvv=***");
    }

    @Test
    void disablingMaskingAppliesConsistentlyToMapsAndPayloads() {
        LoggingProperties properties = new LoggingProperties();
        properties.getMasking().setEnabled(false);
        JsonSensitiveDataMasker disabled = new JsonSensitiveDataMasker(properties, new ObjectMapper());

        assertThat(disabled.maskPayload("{\"password\":\"SECRET\"}")).contains("SECRET");
        assertThat((Map<String, Object>) disabled.maskMap(
                Map.of("Authorization", "Bearer SECRET"), MaskingTarget.HEADER))
                .containsEntry("Authorization", "Bearer SECRET");
    }
}
