package com.anil.logging.logging;

import com.anil.logging.model.HttpLogEvent;

public interface HttpLogWriter {
    void write(HttpLogEvent event);
}
