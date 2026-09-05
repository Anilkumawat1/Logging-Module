package com.anil.logging.model;

import java.util.Map;

public class HttpLogEvent extends LogEvent {
    public HttpLogEvent() {
        setType(LogCategory.HTTP_REQUEST);
    }

    public HttpLogEvent requestId(String value) {
        return fieldTyped("request_id", value);
    }

    public HttpLogEvent traceId(String value) {
        return fieldTyped("trace_id", value);
    }

    public HttpLogEvent spanId(String value) {
        return fieldTyped("span_id", value);
    }

    public HttpLogEvent authenticated(boolean value) {
        return fieldTyped("authenticated", value);
    }

    public HttpLogEvent userId(String value) {
        return fieldTyped("user_id", value);
    }

    public HttpLogEvent roles(Object value) {
        return fieldTyped("role_ids", value);
    }

    public HttpLogEvent ip(String value) {
        return fieldTyped("ip", value);
    }

    public HttpLogEvent domain(String value) {
        return fieldTyped("domain", value);
    }

    public HttpLogEvent url(String value) {
        return fieldTyped("url", value);
    }

    public HttpLogEvent endpoint(String value) {
        return fieldTyped("endpoint", value);
    }

    public HttpLogEvent operation(String value) {
        return fieldTyped("operation", value);
    }

    public HttpLogEvent method(String value) {
        return fieldTyped("method", value);
    }

    public HttpLogEvent requestHeaders(Map<String, ?> value) {
        return fieldTyped("request_headers", value);
    }

    public HttpLogEvent responseHeaders(Map<String, ?> value) {
        return fieldTyped("response_headers", value);
    }

    public HttpLogEvent requestParameters(Map<String, ?> value) {
        return fieldTyped("request_parameters", value);
    }

    public HttpLogEvent requestPayload(String value) {
        return fieldTyped("request_payload", value);
    }

    public HttpLogEvent responsePayload(String value) {
        return fieldTyped("response_payload", value);
    }

    public HttpLogEvent responseStatus(int value) {
        return fieldTyped("response_status", value);
    }

    public HttpLogEvent responseTimeMs(long value) {
        return fieldTyped("response_time_ms", value);
    }

    private HttpLogEvent fieldTyped(String key, Object value) {
        field(key, value);
        return this;
    }
}
