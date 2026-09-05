package com.anil.logging.logging;

import com.anil.logging.model.HttpLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jHttpLogWriter implements HttpLogWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.anil.logging.http");
    private final LogEventFormatter formatter;

    public Slf4jHttpLogWriter(LogEventFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void write(HttpLogEvent event) {
        event.field("logger", LOGGER.getName());
        String formatted = formatter.format(event);
        String level = event.getLevel();
        if ("ERROR".equalsIgnoreCase(level)) {
            LOGGER.error(formatted);
        } else if ("WARN".equalsIgnoreCase(level)) {
            LOGGER.warn(formatted);
        } else if ("DEBUG".equalsIgnoreCase(level)) {
            LOGGER.debug(formatted);
        } else {
            LOGGER.info(formatted);
        }
    }
}
