package com.anil.logging.context;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface LoggingContextManager {
    LoggingContextSnapshot capture();

    void restore(LoggingContextSnapshot context);

    void clear();

    default Runnable wrap(Runnable runnable) {
        LoggingContextSnapshot snapshot = capture();
        return () -> {
            LoggingContextSnapshot previous = capture();
            try {
                restore(snapshot);
                runnable.run();
            } finally {
                restore(previous);
            }
        };
    }

    default <T> Callable<T> wrap(Callable<T> callable) {
        LoggingContextSnapshot snapshot = capture();
        return () -> {
            LoggingContextSnapshot previous = capture();
            try {
                restore(snapshot);
                return callable.call();
            } finally {
                restore(previous);
            }
        };
    }

    default <T> Supplier<T> wrap(Supplier<T> supplier) {
        LoggingContextSnapshot snapshot = capture();
        return () -> {
            LoggingContextSnapshot previous = capture();
            try {
                restore(snapshot);
                return supplier.get();
            } finally {
                restore(previous);
            }
        };
    }

    default <T, R> Function<T, R> wrap(Function<T, R> function) {
        LoggingContextSnapshot snapshot = capture();
        return value -> {
            LoggingContextSnapshot previous = capture();
            try {
                restore(snapshot);
                return function.apply(value);
            } finally {
                restore(previous);
            }
        };
    }

    default <T> Consumer<T> wrap(Consumer<T> consumer) {
        LoggingContextSnapshot snapshot = capture();
        return value -> {
            LoggingContextSnapshot previous = capture();
            try {
                restore(snapshot);
                consumer.accept(value);
            } finally {
                restore(previous);
            }
        };
    }
}
