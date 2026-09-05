package com.anil.logging.logging;

import com.anil.logging.model.LogCategory;

public interface FluentLogEvent {
    FluentLogEvent category(LogCategory category);

    FluentLogEvent field(String key, Object value);

    void log();
}
