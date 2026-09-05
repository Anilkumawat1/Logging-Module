package com.anil.logging.async;

import com.anil.logging.context.LoggingContextManager;

import java.util.Objects;
import java.util.concurrent.Executor;

public class ContextAwareExecutor implements Executor {
    private final Executor delegate;
    private final LoggingContextManager contextManager;

    public ContextAwareExecutor(Executor delegate, LoggingContextManager contextManager) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager");
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(contextManager.wrap(command));
    }
}
