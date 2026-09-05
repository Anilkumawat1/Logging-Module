package com.anil.logging.demo;

import com.anil.logging.async.LoggingExecutors;
import com.anil.logging.context.LoggingContextManager;
import com.anil.logging.logging.LoggingService;
import com.anil.logging.model.LogCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final LoggingService loggingService;
    private final LoggingContextManager contextManager;
    private final ExecutorService executor;

    public OrderService(LoggingService loggingService, LoggingContextManager contextManager) {
        this.loggingService = loggingService;
        this.contextManager = contextManager;
        this.executor = LoggingExecutors.wrap(Executors.newFixedThreadPool(4), contextManager);
    }

    public Map<String, Object> create(Map<String, Object> order) {
        log.info("Starting order processing");
        loggingService.info(LogCategory.BUSINESS, "Order created", Map.of("amount", order.getOrDefault("amount", 0)));
        executor.submit(() -> {
            log.info("Processing payment");
            executor.submit(() -> log.info("Sending notification"));
        });
        return Map.of("status", "CREATED");
    }

    public CompletableFuture<Void> completableFutureWork() {
        return CompletableFuture.runAsync(() -> log.info("CompletableFuture payment task"), executor)
                .thenRunAsync(contextManager.wrap(() -> log.info("CompletableFuture notification task")), executor);
    }

    @Async
    public CompletableFuture<Void> asyncWork() {
        log.info("@Async work retained MDC context");
        return CompletableFuture.completedFuture(null);
    }
}
