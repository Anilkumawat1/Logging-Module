package com.anil.logging.context;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface LoggingContextManager {
    /** Capture the allowlisted context that is safe to propagate to another thread. */
    LoggingContextSnapshot capture();

    /** Restore a propagation snapshot. */
    void restore(LoggingContextSnapshot context);

    /**
     * Capture every MDC entry for same-thread scope restoration. Implementations that do not
     * distinguish raw and propagated state remain source-compatible through this default.
     */
    default LoggingContextSnapshot captureRaw() {
        return capture();
    }

    /** Restore a raw same-thread snapshot without applying propagation filtering. */
    default void restoreRaw(LoggingContextSnapshot context) {
        restore(context);
    }

    void clear();

    default Runnable wrap(Runnable runnable) {
        LoggingContextSnapshot snapshot = capture();
        return () -> {
            LoggingContextSnapshot previous = captureRaw();
            try {
                restore(snapshot);
                runnable.run();
            } finally {
                restoreRaw(previous);
            }
        };
    }

    default <T> Callable<T> wrap(Callable<T> callable) {
        LoggingContextSnapshot snapshot = capture();
        return () -> {
            LoggingContextSnapshot previous = captureRaw();
            try {
                restore(snapshot);
                return callable.call();
            } finally {
                restoreRaw(previous);
            }
        };
    }

    default <T> Supplier<T> wrap(Supplier<T> supplier) {
        LoggingContextSnapshot snapshot = capture();
        return () -> {
            LoggingContextSnapshot previous = captureRaw();
            try {
                restore(snapshot);
                return supplier.get();
            } finally {
                restoreRaw(previous);
            }
        };
    }

    default <T, R> Function<T, R> wrap(Function<T, R> function) {
        LoggingContextSnapshot snapshot = capture();
        return value -> {
            LoggingContextSnapshot previous = captureRaw();
            try {
                restore(snapshot);
                return function.apply(value);
            } finally {
                restoreRaw(previous);
            }
        };
    }

    default <T> Consumer<T> wrap(Consumer<T> consumer) {
        LoggingContextSnapshot snapshot = capture();
        return value -> {
            LoggingContextSnapshot previous = captureRaw();
            try {
                restore(snapshot);
                consumer.accept(value);
            } finally {
                restoreRaw(previous);
            }
        };
    }
}
