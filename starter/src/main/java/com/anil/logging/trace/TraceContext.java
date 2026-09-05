package com.anil.logging.trace;

public record TraceContext(String traceId, String spanId) {
    public boolean hasTrace() {
        return traceId != null && !traceId.isBlank();
    }
}
