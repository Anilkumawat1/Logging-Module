package com.anil.logging.config;

import com.anil.logging.model.LogCategory;
import com.anil.logging.model.LogFormat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingPropertiesBindingTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertyConfiguration.class);

    @Test
    void bindsEveryConfigurationGroup() {
        contextRunner.withPropertyValues(
                "app.logging.enabled=false",
                "app.logging.service-name=checkout",
                "app.logging.environment=staging",
                "app.logging.format=text",
                "app.logging.include.request-headers=true",
                "app.logging.include.response-headers=true",
                "app.logging.include.request-parameters=false",
                "app.logging.include.request-payload=false",
                "app.logging.include.response-payload=true",
                "app.logging.include.user=false",
                "app.logging.include.ip=false",
                "app.logging.include.domain=false",
                "app.logging.include.url=false",
                "app.logging.include.operation=false",
                "app.logging.include.trace=false",
                "app.logging.include.thread=false",
                "app.logging.include.logger=false",
                "app.logging.include.caller=true",
                "app.logging.payload.request-max-size=123",
                "app.logging.payload.response-max-size=456",
                "app.logging.payload.log-binary=true",
                "app.logging.request-payload.on-status-ranges[0]=400-499",
                "app.logging.request-payload.on-status-ranges[1]=503",
                "app.logging.masking.enabled=false",
                "app.logging.masking.replacement=[hidden]",
                "app.logging.masking.fields[0]=pin",
                "app.logging.masking.headers[0]=x-secret",
                "app.logging.masking.query-parameters[0]=code",
                "app.logging.ip.headers[0]=Forwarded-Client-IP",
                "app.logging.excluded.paths[0]=/internal/**",
                "app.logging.excluded.methods[0]=HEAD",
                "app.logging.excluded.content-types[0]=application/zip",
                "app.logging.operations.mappings[POST /orders]=CREATE_ORDER",
                "app.logging.categories.HTTP_REQUEST=false",
                "app.logging.categories.SECURITY=false",
                "app.logging.levels.success=DEBUG",
                "app.logging.levels.client-error=INFO",
                "app.logging.levels.server-error=WARN",
                "app.logging.context.fields.request-id=false",
                "app.logging.context.fields.trace-id=false",
                "app.logging.context.fields.span-id=false",
                "app.logging.context.fields.user-id=false",
                "app.logging.context.fields.roles=false",
                "app.logging.context.fields.service=false",
                "app.logging.context.fields.environment=false",
                "app.logging.context.propagation.enabled=false",
                "app.logging.context.propagation.allowed-fields[0]=tenant_id",
                "app.logging.async.enabled=false",
                "app.logging.async.task-decorator=false",
                "app.logging.async.executor-integration=false",
                "app.logging.identity.user-id-claims[0]=uid",
                "app.logging.identity.role-id-claims[0]=groups")
                .run(context -> {
                    LoggingProperties p = context.getBean(LoggingProperties.class);
                    assertThat(p.isEnabled()).isFalse();
                    assertThat(p.getServiceName()).isEqualTo("checkout");
                    assertThat(p.getEnvironment()).isEqualTo("staging");
                    assertThat(p.getFormat()).isEqualTo(LogFormat.TEXT);
                    assertThat(p.getInclude().isRequestHeaders()).isTrue();
                    assertThat(p.getInclude().isResponseHeaders()).isTrue();
                    assertThat(p.getInclude().isRequestParameters()).isFalse();
                    assertThat(p.getInclude().isRequestPayload()).isFalse();
                    assertThat(p.getInclude().isResponsePayload()).isTrue();
                    assertThat(p.getInclude().isUser()).isFalse();
                    assertThat(p.getInclude().isIp()).isFalse();
                    assertThat(p.getInclude().isDomain()).isFalse();
                    assertThat(p.getInclude().isUrl()).isFalse();
                    assertThat(p.getInclude().isOperation()).isFalse();
                    assertThat(p.getInclude().isTrace()).isFalse();
                    assertThat(p.getInclude().isThread()).isFalse();
                    assertThat(p.getInclude().isLogger()).isFalse();
                    assertThat(p.getInclude().isCaller()).isTrue();
                    assertThat(p.getPayload().getRequestMaxSize()).isEqualTo(123);
                    assertThat(p.getPayload().getResponseMaxSize()).isEqualTo(456);
                    assertThat(p.getPayload().isLogBinary()).isTrue();
                    assertThat(p.getRequestPayload().getOnStatusRanges()).containsExactly("400-499", "503");
                    assertThat(p.getMasking().isEnabled()).isFalse();
                    assertThat(p.getMasking().getReplacement()).isEqualTo("[hidden]");
                    assertThat(p.getMasking().getFields()).containsExactly("pin");
                    assertThat(p.getMasking().getHeaders()).containsExactly("x-secret");
                    assertThat(p.getMasking().getQueryParameters()).containsExactly("code");
                    assertThat(p.getIp().getHeaders()).containsExactly("Forwarded-Client-IP");
                    assertThat(p.getExcluded().getPaths()).containsExactly("/internal/**");
                    assertThat(p.getExcluded().getMethods()).containsExactly("HEAD");
                    assertThat(p.getExcluded().getContentTypes()).containsExactly("application/zip");
                    assertThat(p.getOperations().getMappings()).containsEntry("POST /orders", "CREATE_ORDER");
                    assertThat(p.categoryEnabled(LogCategory.HTTP_REQUEST)).isFalse();
                    assertThat(p.categoryEnabled(LogCategory.SECURITY)).isFalse();
                    assertThat(p.getLevels().getSuccess()).isEqualTo("DEBUG");
                    assertThat(p.getLevels().getClientError()).isEqualTo("INFO");
                    assertThat(p.getLevels().getServerError()).isEqualTo("WARN");
                    assertThat(p.getContext().getFields().isRequestId()).isFalse();
                    assertThat(p.getContext().getFields().isTraceId()).isFalse();
                    assertThat(p.getContext().getFields().isSpanId()).isFalse();
                    assertThat(p.getContext().getFields().isUserId()).isFalse();
                    assertThat(p.getContext().getFields().isRoles()).isFalse();
                    assertThat(p.getContext().getFields().isService()).isFalse();
                    assertThat(p.getContext().getFields().isEnvironment()).isFalse();
                    assertThat(p.getContext().getPropagation().isEnabled()).isFalse();
                    assertThat(p.getContext().getPropagation().getAllowedFields()).containsExactly("tenant_id");
                    assertThat(p.getAsync().isEnabled()).isFalse();
                    assertThat(p.getAsync().isTaskDecorator()).isFalse();
                    assertThat(p.getAsync().isExecutorIntegration()).isFalse();
                    assertThat(p.getIdentity().getUserIdClaims()).containsExactly("uid");
                    assertThat(p.getIdentity().getRoleIdClaims()).containsExactly("groups");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoggingProperties.class)
    static class PropertyConfiguration {
    }
}
