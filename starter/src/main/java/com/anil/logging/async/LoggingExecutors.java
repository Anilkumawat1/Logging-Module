package com.anil.logging.async;

import com.anil.logging.context.LoggingContextManager;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class LoggingExecutors {
    private LoggingExecutors() {
    }

    public static Executor wrap(Executor executor, LoggingContextManager contextManager) {
        return new ContextAwareExecutor(executor, contextManager);
    }

    public static ExecutorService wrap(ExecutorService executor, LoggingContextManager contextManager) {
        return new ContextAwareExecutorService(executor, contextManager);
    }

    public static ScheduledExecutorService wrap(ScheduledExecutorService executor, LoggingContextManager contextManager) {
        return new ContextAwareScheduledExecutorService(executor, contextManager);
    }

    public static Runnable wrap(Runnable runnable, LoggingContextManager contextManager) {
        return contextManager.wrap(runnable);
    }

    public static <T> Callable<T> wrap(Callable<T> callable, LoggingContextManager contextManager) {
        return contextManager.wrap(callable);
    }

    public static <T> Supplier<T> wrap(Supplier<T> supplier, LoggingContextManager contextManager) {
        return contextManager.wrap(supplier);
    }

    public static <T, R> Function<T, R> wrap(Function<T, R> function, LoggingContextManager contextManager) {
        return contextManager.wrap(function);
    }

    public static <T> Consumer<T> wrap(Consumer<T> consumer, LoggingContextManager contextManager) {
        return contextManager.wrap(consumer);
    }
}
