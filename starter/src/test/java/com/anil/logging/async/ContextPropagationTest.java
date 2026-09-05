package com.anil.logging.async;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.context.MdcKeys;
import com.anil.logging.context.MdcLoggingContextManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextPropagationTest {
    private final LoggingProperties properties = new LoggingProperties();
    private final MdcLoggingContextManager contextManager = new MdcLoggingContextManager(properties);

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void executorTaskRetainsParentRequestIdAndRestoresPreviousWorkerContext() throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = LoggingExecutors.wrap(raw, contextManager);
        raw.submit(() -> MDC.put("workerField", "keep-me")).get();

        MDC.put(MdcKeys.REQUEST_ID, "abc123");
        Future<String> requestId = wrapped.submit(() -> {
            assertThat(MDC.get("workerField")).isNull();
            return MDC.get(MdcKeys.REQUEST_ID);
        });

        assertThat(requestId.get()).isEqualTo("abc123");
        assertThat(raw.submit(() -> MDC.get("workerField")).get()).isEqualTo("keep-me");
        assertThat(raw.submit(() -> MDC.get(MdcKeys.REQUEST_ID)).get()).isNull();
        raw.shutdownNow();
    }

    @Test
    void nestedExecutorTasksRetainSameContext() throws Exception {
        ExecutorService raw = Executors.newFixedThreadPool(2);
        ExecutorService wrapped = LoggingExecutors.wrap(raw, contextManager);
        MDC.put(MdcKeys.REQUEST_ID, "nested-123");

        Future<String> value = wrapped.submit(() -> wrapped.submit(() -> MDC.get(MdcKeys.REQUEST_ID)).get());

        assertThat(value.get()).isEqualTo("nested-123");
        raw.shutdownNow();
    }

    @Test
    void completableFutureChainRetainsContextWhenUsingWrappedExecutor() {
        ExecutorService raw = Executors.newFixedThreadPool(2);
        ExecutorService wrapped = LoggingExecutors.wrap(raw, contextManager);
        MDC.put(MdcKeys.REQUEST_ID, "cf-123");

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> MDC.get(MdcKeys.REQUEST_ID), wrapped)
                .thenApplyAsync(value -> value + ":" + MDC.get(MdcKeys.REQUEST_ID), wrapped);

        assertThat(future.join()).isEqualTo("cf-123:cf-123");
        raw.shutdownNow();
    }

    @Test
    void reusedThreadDoesNotLeakPreviousRequestContext() throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = LoggingExecutors.wrap(raw, contextManager);

        MDC.put(MdcKeys.REQUEST_ID, "A");
        assertThat(wrapped.submit(() -> MDC.get(MdcKeys.REQUEST_ID)).get()).isEqualTo("A");

        MDC.put(MdcKeys.REQUEST_ID, "B");
        assertThat(wrapped.submit(() -> MDC.get(MdcKeys.REQUEST_ID)).get()).isEqualTo("B");
        assertThat(raw.submit(() -> MDC.get(MdcKeys.REQUEST_ID)).get()).isNull();
        raw.shutdownNow();
    }

    @Test
    void exceptionInsideAsyncTaskDoesNotLeakContext() throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = LoggingExecutors.wrap(raw, contextManager);
        MDC.put(MdcKeys.REQUEST_ID, "boom-123");

        Future<?> failed = wrapped.submit(() -> {
            assertThat(MDC.get(MdcKeys.REQUEST_ID)).isEqualTo("boom-123");
            throw new IllegalStateException("failure");
        });

        assertThatThrownBy(failed::get).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(raw.submit(() -> MDC.get(MdcKeys.REQUEST_ID)).get()).isNull();
        raw.shutdownNow();
    }

    @Test
    void sensitiveFieldsAreNotPropagatedEvenWhenPresentInMdc() throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = LoggingExecutors.wrap(raw, contextManager);
        MDC.put(MdcKeys.REQUEST_ID, "safe");
        MDC.put("accessToken", "SECRET");

        Future<String> value = wrapped.submit(() -> MDC.get(MdcKeys.REQUEST_ID) + ":" + MDC.get("accessToken"));

        assertThat(value.get()).isEqualTo("safe:null");
        raw.shutdownNow();
    }
}
