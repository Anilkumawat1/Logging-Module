package com.anil.logging.async;

import com.anil.logging.context.LoggingContextManager;
import org.springframework.core.task.TaskDecorator;

public class LoggingTaskDecorator implements TaskDecorator {
    private final LoggingContextManager contextManager;

    public LoggingTaskDecorator(LoggingContextManager contextManager) {
        this.contextManager = contextManager;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        return contextManager.wrap(runnable);
    }
}
