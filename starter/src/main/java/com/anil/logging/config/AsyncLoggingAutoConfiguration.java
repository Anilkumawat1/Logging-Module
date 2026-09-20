package com.anil.logging.config;

import com.anil.logging.async.LoggingTaskDecorator;
import com.anil.logging.context.LoggingContextManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.SimpleAsyncTaskExecutorCustomizer;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
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
    @ConditionalOnClass(name = "org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer")
    static class TaskExecutorCustomizerConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "app.logging.async", name = {"task-decorator", "executor-integration"}, havingValue = "true", matchIfMissing = true)
        ThreadPoolTaskExecutorCustomizer loggingThreadPoolTaskExecutorCustomizer(
                ObjectProvider<LoggingTaskDecorator> decoratorProvider,
                LoggingContextManager contextManager) {
            LoggingTaskDecorator decorator = decoratorProvider.getIfAvailable(
                    () -> new LoggingTaskDecorator(contextManager));
            return executor -> executor.setTaskDecorator(decorator);
        }

        @Bean
        @ConditionalOnClass(SimpleAsyncTaskExecutorCustomizer.class)
        @ConditionalOnProperty(prefix = "app.logging.async", name = {"task-decorator", "executor-integration"}, havingValue = "true", matchIfMissing = true)
        SimpleAsyncTaskExecutorCustomizer loggingSimpleAsyncTaskExecutorCustomizer(
                ObjectProvider<LoggingTaskDecorator> decoratorProvider,
                LoggingContextManager contextManager) {
            LoggingTaskDecorator decorator = decoratorProvider.getIfAvailable(
                    () -> new LoggingTaskDecorator(contextManager));
            return executor -> executor.setTaskDecorator(decorator);
        }
    }
}
