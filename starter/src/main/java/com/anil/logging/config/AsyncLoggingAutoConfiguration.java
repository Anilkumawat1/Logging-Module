package com.anil.logging.config;

import com.anil.logging.async.LoggingTaskDecorator;
import com.anil.logging.context.LoggingContextManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.TaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = LoggingAutoConfiguration.class)
@EnableConfigurationProperties(LoggingProperties.class)
@ConditionalOnProperty(prefix = "app.logging.async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncLoggingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.logging.async", name = "task-decorator", havingValue = "true", matchIfMissing = true)
    LoggingTaskDecorator loggingTaskDecorator(LoggingContextManager contextManager) {
        return new LoggingTaskDecorator(contextManager);
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.task.TaskExecutorCustomizer")
    static class TaskExecutorCustomizerConfiguration {

        @Bean
        @ConditionalOnBean(LoggingTaskDecorator.class)
        @ConditionalOnProperty(prefix = "app.logging.async", name = "executor-integration", havingValue = "true", matchIfMissing = true)
        TaskExecutorCustomizer loggingTaskExecutorCustomizer(LoggingTaskDecorator decorator) {
            return executor -> executor.setTaskDecorator(decorator);
        }
    }
}
