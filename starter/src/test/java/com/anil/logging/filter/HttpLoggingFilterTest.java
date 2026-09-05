package com.anil.logging.filter;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.context.MdcKeys;
import com.anil.logging.context.MdcLoggingContextManager;
import com.anil.logging.logging.HttpLogWriter;
import com.anil.logging.masking.JsonSensitiveDataMasker;
import com.anil.logging.model.HttpLogEvent;
import com.anil.logging.model.LogCategory;
import com.anil.logging.operation.DefaultOperationResolver;
import com.anil.logging.security.UserIdentity;
import com.anil.logging.trace.DefaultTraceContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLoggingFilterTest {
    private final LoggingProperties properties = new LoggingProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingWriter writer = new RecordingWriter();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void writesOneStructuredHttpEventWithMaskedSecurityFields() throws Exception {
        properties.getInclude().setRequestHeaders(true);
        properties.getInclude().setResponseHeaders(true);
        properties.getInclude().setResponsePayload(true);
        properties.getOperations().getMappings().put("POST /api/orders", "CREATE_ORDER");
        HttpLoggingFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setServerName("orders.example.test");
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Request-ID", "abc123");
        request.addHeader("Authorization", "Bearer SECRET");
        request.addHeader("postman-token", "POSTMAN_SECRET");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        request.setContentType("application/json");
        request.setContent("""
                {"orderId":"1001","password":"secret","nested":{"cvv":"123"}}
                """.getBytes(StandardCharsets.UTF_8));
        request.addParameter("token", "QUERY_SECRET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) res;
            req.getInputStream().readAllBytes();
            httpResponse.setStatus(422);
            res.setContentType("application/json");
            httpResponse.addHeader("Set-Cookie", "session=SECRET");
            res.getWriter().write("{\"status\":\"invalid\"}");
        };

        filter.doFilter(request, response, chain);

        assertThat(writer.events).hasSize(2);
        HttpLogEvent requestEvent = writer.events.get(0);
        HttpLogEvent responseEvent = writer.events.get(1);
        assertThat(requestEvent.getType()).isEqualTo(LogCategory.HTTP_REQUEST);
        assertThat(requestEvent.getMessage()).isEqualTo("HTTP request POST /api/orders");
        assertThat(requestEvent.getFields()).containsEntry("request_id", "abc123")
                .containsEntry("method", "POST")
                .containsEntry("endpoint", "/api/orders")
                .containsEntry("operation", "CREATE_ORDER");
        assertThat((Map<String, Object>) requestEvent.getFields().get("request_headers")).containsEntry("Authorization", "***");
        assertThat((Map<String, Object>) requestEvent.getFields().get("request_headers")).containsEntry("postman-token", "***");
        assertThat((Map<String, Object>) requestEvent.getFields().get("request_parameters")).containsEntry("token", "***");

        assertThat(responseEvent.getType()).isEqualTo(LogCategory.HTTP_RESPONSE);
        assertThat(responseEvent.getMessage()).isEqualTo("HTTP response POST /api/orders 422");
        assertThat(responseEvent.getFields()).containsEntry("request_id", "abc123")
                .containsEntry("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736")
                .containsEntry("span_id", "00f067aa0ba902b7")
                .containsEntry("user_id", "1001")
                .containsEntry("role_ids", List.of("ADMIN"))
                .containsEntry("operation", "CREATE_ORDER")
                .containsEntry("method", "POST")
                .containsEntry("endpoint", "/api/orders")
                .containsEntry("response_status", 422);
        assertThat((Map<String, Object>) responseEvent.getFields().get("response_headers")).containsEntry("Set-Cookie", "***");
        assertThat((String) responseEvent.getFields().get("request_payload")).doesNotContain("secret", "123").contains("\"password\":\"***\"", "\"cvv\":\"***\"");
        assertThat((String) responseEvent.getFields().get("response_payload")).contains("invalid");
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
    }

    @Test
    void omitsBinaryPayloadsAndSuccessfulRequestPayloadByDefault() throws Exception {
        HttpLoggingFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload");
        request.addHeader("X-Request-ID", "binary");
        request.setContentType("application/octet-stream");
        request.setContent(new byte[]{1, 2, 3});
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, chain);

        assertThat(writer.events).hasSize(2);
        assertThat(writer.events.get(1).getFields()).doesNotContainKeys("request_payload", "response_payload");
    }

    private HttpLoggingFilter filter() {
        JsonSensitiveDataMasker masker = new JsonSensitiveDataMasker(properties, objectMapper);
        return new HttpLoggingFilter(properties,
                new MdcLoggingContextManager(properties),
                masker,
                new DefaultClientIpResolver(properties),
                () -> java.util.Optional.of(new UserIdentity("1001", List.of("ADMIN"), true)),
                new DefaultTraceContextProvider(),
                new DefaultOperationResolver(properties),
                writer,
                "order-service",
                "test");
    }

    private static class RecordingWriter implements HttpLogWriter {
        private final List<HttpLogEvent> events = new ArrayList<>();

        @Override
        public void write(HttpLogEvent event) {
            this.events.add(event);
        }
    }
}
