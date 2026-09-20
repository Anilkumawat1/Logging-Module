package com.anil.logging.config;

import com.anil.logging.async.LoggingTaskDecorator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.task.SimpleAsyncTaskExecutorCustomizer;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncLoggingAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LoggingAutoConfiguration.class,
                    AsyncLoggingAutoConfiguration.class));

    @Test
    void createsDecoratorAndBothBootExecutorCustomizersByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LoggingTaskDecorator.class);
            assertThat(context).hasSingleBean(ThreadPoolTaskExecutorCustomizer.class);
            assertThat(context).hasSingleBean(SimpleAsyncTaskExecutorCustomizer.class);
        });
    }

    @Test
    void asyncMasterSwitchDisablesDecoratorAndExecutorIntegration() {
        contextRunner.withPropertyValues("app.logging.async.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LoggingTaskDecorator.class);
                    assertThat(context).doesNotHaveBean(ThreadPoolTaskExecutorCustomizer.class);
                    assertThat(context).doesNotHaveBean(SimpleAsyncTaskExecutorCustomizer.class);
                });
    }

    @Test
    void individualAsyncSwitchesWorkIndependently() {
        contextRunner.withPropertyValues("app.logging.async.executor-integration=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(LoggingTaskDecorator.class);
                    assertThat(context).doesNotHaveBean(ThreadPoolTaskExecutorCustomizer.class);
                    assertThat(context).doesNotHaveBean(SimpleAsyncTaskExecutorCustomizer.class);
                });

        contextRunner.withPropertyValues("app.logging.async.task-decorator=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LoggingTaskDecorator.class);
                    assertThat(context).doesNotHaveBean(ThreadPoolTaskExecutorCustomizer.class);
                    assertThat(context).doesNotHaveBean(SimpleAsyncTaskExecutorCustomizer.class);
                });
    }
}
