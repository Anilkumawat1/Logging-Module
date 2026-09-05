package com.anil.logging.async;

import com.anil.logging.context.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AsyncAnnotationPropagationTest.Config.class)
class AsyncAnnotationPropagationTest {
    @Autowired
    AsyncService asyncService;

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void asyncMethodRetainsRequestContext() {
        MDC.put(MdcKeys.REQUEST_ID, "async-123");

        assertThat(asyncService.requestId().join()).isEqualTo("async-123");
    }

    @EnableAsync
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Config {
        @Bean
        AsyncService asyncService() {
            return new AsyncService();
        }
    }

    static class AsyncService {
        @Async
        public CompletableFuture<String> requestId() {
            return CompletableFuture.completedFuture(MDC.get(MdcKeys.REQUEST_ID));
        }
    }
}
