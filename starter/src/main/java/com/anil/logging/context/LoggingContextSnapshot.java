package com.anil.logging.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LoggingContextSnapshot {
    private final Map<String, String> values;

    public LoggingContextSnapshot(Map<String, String> values) {
        this.values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public Map<String, String> values() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
