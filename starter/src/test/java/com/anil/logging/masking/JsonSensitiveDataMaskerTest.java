package com.anil.logging.masking;

import com.anil.logging.config.LoggingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
