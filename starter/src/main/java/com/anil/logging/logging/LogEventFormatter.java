package com.anil.logging.logging;

import com.anil.logging.model.LogEvent;

public interface LogEventFormatter {
    String format(LogEvent event);
}
