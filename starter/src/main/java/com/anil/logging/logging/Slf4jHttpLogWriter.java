package com.anil.logging.logging;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.model.HttpLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jHttpLogWriter implements HttpLogWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.anil.logging.http");
    private final LogEventFormatter formatter;
    private final LoggingProperties properties;

    /**
     * @deprecated Prefer the constructor that accepts {@link LoggingProperties} so include flags
     * are honored. Kept for source compatibility with early starter users.
     */
    @Deprecated(forRemoval = false)
    public Slf4jHttpLogWriter(LogEventFormatter formatter) {
        this(formatter, new LoggingProperties());
    }

    public Slf4jHttpLogWriter(LogEventFormatter formatter, LoggingProperties properties) {
        this.formatter = formatter;
        this.properties = properties;
    }

    @Override
    public void write(HttpLogEvent event) {
        if (properties.getInclude().isLogger()) {
            event.field("logger", LOGGER.getName());
        }
        String formatted = formatter.format(event);
        String level = event.getLevel();
        if ("ERROR".equalsIgnoreCase(level)) {
            LOGGER.error(formatted);
        } else if ("WARN".equalsIgnoreCase(level)) {
            LOGGER.warn(formatted);
        } else if ("DEBUG".equalsIgnoreCase(level)) {
            LOGGER.debug(formatted);
        } else if ("TRACE".equalsIgnoreCase(level)) {
            LOGGER.trace(formatted);
        } else {
            LOGGER.info(formatted);
        }
    }
}
