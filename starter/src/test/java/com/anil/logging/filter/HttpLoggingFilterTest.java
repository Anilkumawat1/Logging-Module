package com.anil.logging.filter;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.context.MdcKeys;
import com.anil.logging.context.MdcLoggingContextManager;
import com.anil.logging.logging.HttpLogWriter;
import com.anil.logging.masking.JsonSensitiveDataMasker;
import com.anil.logging.model.HttpLogEvent;
import com.anil.logging.model.LogCategory;
import com.anil.logging.operation.DefaultOperationResolver;
import com.anil.logging.security.DefaultUserIdentityProvider;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        SecurityContextHolder.clearContext();
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
                // operation is intentionally absent from the request event: it is only
                // available after the DispatcherServlet processes the request (response event).
                .doesNotContainKey("operation");
        assertThat((Map<String, Object>) requestEvent.getFields().get("request_headers")).containsEntry("Authorization", "***");
        assertThat((Map<String, Object>) requestEvent.getFields().get("request_headers")).containsEntry("postman-token", "***");
        assertThat((Map<String, Object>) requestEvent.getFields().get("request_parameters")).containsEntry("token", "***");

        assertThat(responseEvent.getType()).isEqualTo(LogCategory.HTTP_RESPONSE);
        // Status code is now first in the message for easier grep: 'HTTP response 422 POST /api/orders'
        assertThat(responseEvent.getMessage()).isEqualTo("HTTP response 422 POST /api/orders");
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
    void includesSuccessfulJsonRequestPayloadWhenBodyWasRead() throws Exception {
        properties.getInclude().setResponsePayload(true);
        HttpLoggingFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Request-ID", "success");
        request.setContentType("application/json");
        request.setContent("{\"order_id\":\"1001\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(201);
            res.setContentType("application/json");
            res.getWriter().write("[{\"status\":\"CREATED\"}]");
        };

        filter.doFilter(request, response, chain);

        assertThat(writer.events).hasSize(2);
        HttpLogEvent responseEvent = writer.events.get(1);
        assertThat(responseEvent.getFields()).containsEntry("response_status", 201);
        assertThat(responseEvent.getFields().get("request_payload")).isEqualTo("{\"order_id\":\"1001\"}");
        assertThat(responseEvent.getFields().get("response_payload")).isEqualTo("[{\"status\":\"CREATED\"}]");
    }

    @Test
    void omitsBinaryPayloads() throws Exception {
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

    @Test
    void requestPayloadOmittedFromResponseEventWhenStatusNotInConfiguredRange() throws Exception {
        // on-status-ranges: 400-599  →  should NOT appear for a 200 response
        properties.getRequestPayload().setOnStatusRanges(List.of("400-599"));
        HttpLoggingFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType("application/json");
        request.setContent("{\"amount\":2}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, chain);

        HttpLogEvent responseEvent = writer.events.get(1);
        assertThat(responseEvent.getFields()).doesNotContainKey("request_payload");
    }

    @Test
    void requestPayloadIncludedInResponseEventWhenStatusIsInConfiguredRange() throws Exception {
        // on-status-ranges: 400-599  →  SHOULD appear for a 422 response
        properties.getRequestPayload().setOnStatusRanges(List.of("400-599"));
        HttpLoggingFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType("application/json");
        request.setContent("{\"amount\":2}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(422);
        };

        filter.doFilter(request, response, chain);

        HttpLogEvent responseEvent = writer.events.get(1);
        assertThat(responseEvent.getFields().get("request_payload")).isEqualTo("{\"amount\":2}");
    }

    @Test
    void requestPayloadAlwaysIncludedWhenDefaultRangeIsUsed() throws Exception {
        // Default on-status-ranges is "100-599" (everything) — payload appears on any status
        HttpLoggingFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType("application/json");
        request.setContent("{\"amount\":5}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(201);
        };

        filter.doFilter(request, response, chain);

        HttpLogEvent responseEvent = writer.events.get(1);
        assertThat(responseEvent.getFields().get("request_payload")).isEqualTo("{\"amount\":5}");
    }

    @Test
    void jwtUserIdAndRolesArePopulatedInLogEventsFromJwtClaims() throws Exception {
        // Wire the real DefaultUserIdentityProvider backed by Spring Security context.
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("user_id", "jwt-user-42")
                .claim("role_ids", List.of("ADMIN", "MANAGER"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, "test-token", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        HttpLoggingFilter filter = filterWithRealIdentityProvider();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile");
        request.addHeader("X-Request-ID", "jwt-req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

        filter.doFilter(request, response, chain);

        assertThat(writer.events).hasSize(2);
        HttpLogEvent requestEvent = writer.events.get(0);
        HttpLogEvent responseEvent = writer.events.get(1);
        // Both request and response events should carry the JWT-derived identity
        assertThat(requestEvent.getFields()).containsEntry("user_id", "jwt-user-42")
                .containsEntry("role_ids", List.of("ADMIN", "MANAGER"))
                .containsEntry("authenticated", true);
        assertThat(responseEvent.getFields()).containsEntry("user_id", "jwt-user-42")
                .containsEntry("role_ids", List.of("ADMIN", "MANAGER"));
        // MDC should be cleaned up after request
        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
    }

    @Test
    void anonymousRequestDoesNotIncludeUserIdOrRolesInLogEvents() throws Exception {
        AnonymousAuthenticationToken anonymousToken = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymousToken);

        HttpLoggingFilter filter = filterWithRealIdentityProvider();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public");
        request.addHeader("X-Request-ID", "anon-req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

        filter.doFilter(request, response, chain);

        assertThat(writer.events).hasSize(2);
        HttpLogEvent responseEvent = writer.events.get(1);
        // user_id and role_ids must NOT be present for anonymous requests
        assertThat(responseEvent.getFields()).doesNotContainKey("user_id");
        assertThat(responseEvent.getFields()).doesNotContainKey("role_ids");
        assertThat(responseEvent.getFields()).containsEntry("authenticated", false);
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

    /** Uses the real {@link DefaultUserIdentityProvider} backed by Spring Security context. */
    private HttpLoggingFilter filterWithRealIdentityProvider() {
        JsonSensitiveDataMasker masker = new JsonSensitiveDataMasker(properties, objectMapper);
        return new HttpLoggingFilter(properties,
                new MdcLoggingContextManager(properties),
                masker,
                new DefaultClientIpResolver(properties),
                new DefaultUserIdentityProvider(properties),
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
