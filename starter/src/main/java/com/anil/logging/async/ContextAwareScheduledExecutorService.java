package com.anil.logging.async;

import com.anil.logging.context.LoggingContextManager;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ContextAwareScheduledExecutorService extends ContextAwareExecutorService implements ScheduledExecutorService {
    private final ScheduledExecutorService scheduledDelegate;

    public ContextAwareScheduledExecutorService(ScheduledExecutorService delegate, LoggingContextManager contextManager) {
        super(delegate, contextManager);
        this.scheduledDelegate = delegate;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduledDelegate.schedule(contextManager.wrap(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return scheduledDelegate.schedule(contextManager.wrap(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduledDelegate.scheduleAtFixedRate(contextManager.wrap(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduledDelegate.scheduleWithFixedDelay(contextManager.wrap(command), initialDelay, delay, unit);
    }
}
