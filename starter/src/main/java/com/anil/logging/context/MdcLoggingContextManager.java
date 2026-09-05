package com.anil.logging.context;

import com.anil.logging.config.LoggingProperties;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MdcLoggingContextManager implements LoggingContextManager {
    private final LoggingProperties properties;
    private final Set<String> allowedFields;
    private final Set<String> deniedFields;

    public MdcLoggingContextManager(LoggingProperties properties) {
        this.properties = properties;
        this.allowedFields = lower(properties.getContext().getPropagation().getAllowedFields());
        this.deniedFields = lower(properties.getMasking().allSensitiveFieldNames());
    }

    @Override
    public LoggingContextSnapshot capture() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap == null || contextMap.isEmpty()) {
            return new LoggingContextSnapshot(Map.of());
        }
        if (!properties.getContext().getPropagation().isEnabled()) {
            return new LoggingContextSnapshot(Map.of());
        }
        Map<String, String> captured = new LinkedHashMap<>();
        contextMap.forEach((key, value) -> {
            String normalized = normalize(key);
            if (allowedFields.contains(normalized) && !deniedFields.contains(normalized)) {
                captured.put(key, value);
            }
        });
        return new LoggingContextSnapshot(captured);
    }

    @Override
    public void restore(LoggingContextSnapshot context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context.values());
    }

    @Override
    public void clear() {
        MDC.clear();
    }

    @Override
    public Runnable wrap(Runnable runnable) {
        LoggingContextSnapshot snapshot = capture();
        return () -> runWithRawRestore(snapshot, runnable);
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> callable) {
        LoggingContextSnapshot snapshot = capture();
        return () -> callWithRawRestore(snapshot, callable);
    }

    @Override
    public <T> Supplier<T> wrap(Supplier<T> supplier) {
        LoggingContextSnapshot snapshot = capture();
        return () -> {
            try {
                return callWithRawRestore(snapshot, supplier::get);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
    }

    @Override
    public <T, R> Function<T, R> wrap(Function<T, R> function) {
        LoggingContextSnapshot snapshot = capture();
        return value -> {
            try {
                return callWithRawRestore(snapshot, () -> function.apply(value));
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
    }

    @Override
    public <T> Consumer<T> wrap(Consumer<T> consumer) {
        LoggingContextSnapshot snapshot = capture();
        return value -> runWithRawRestore(snapshot, () -> consumer.accept(value));
    }

    private void runWithRawRestore(LoggingContextSnapshot snapshot, Runnable runnable) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            restore(snapshot);
            runnable.run();
        } finally {
            restoreRaw(previous);
        }
    }

    private <T> T callWithRawRestore(LoggingContextSnapshot snapshot, Callable<T> callable) throws Exception {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            restore(snapshot);
            return callable.call();
        } finally {
            restoreRaw(previous);
        }
    }

    private static void restoreRaw(Map<String, String> previous) {
        if (previous == null || previous.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }

    private static Set<String> lower(Iterable<String> values) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String value : values) {
            result.put(normalize(value), Boolean.TRUE);
        }
        return result.keySet().stream().collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
