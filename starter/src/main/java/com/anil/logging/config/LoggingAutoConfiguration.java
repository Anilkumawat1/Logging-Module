package com.anil.logging.config;

import com.anil.logging.context.LoggingContextManager;
import com.anil.logging.context.MdcLoggingContextManager;
import com.anil.logging.filter.ClientIpResolver;
import com.anil.logging.filter.DefaultClientIpResolver;
import com.anil.logging.filter.HttpLoggingFilter;
import com.anil.logging.logging.AuditLogger;
import com.anil.logging.logging.DefaultLogEventFormatter;
import com.anil.logging.logging.DefaultLoggingService;
import com.anil.logging.logging.HttpLogWriter;
import com.anil.logging.logging.LogEventFormatter;
import com.anil.logging.logging.LoggingService;
import com.anil.logging.logging.Slf4jHttpLogWriter;
import com.anil.logging.masking.JsonSensitiveDataMasker;
import com.anil.logging.masking.SensitiveDataMasker;
import com.anil.logging.operation.DefaultOperationResolver;
import com.anil.logging.operation.OperationLoggingInterceptor;
import com.anil.logging.operation.OperationResolver;
import com.anil.logging.security.AnonymousUserIdentityProvider;
import com.anil.logging.security.DefaultUserIdentityProvider;
import com.anil.logging.security.UserIdentityProvider;
import com.anil.logging.trace.DefaultTraceContextProvider;
import com.anil.logging.trace.TraceContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
@EnableConfigurationProperties(LoggingProperties.class)
public class LoggingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ObjectMapper loggingObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    LoggingContextManager loggingContextManager(LoggingProperties properties) {
        return new MdcLoggingContextManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    SensitiveDataMasker sensitiveDataMasker(LoggingProperties properties, ObjectMapper objectMapper) {
        return new JsonSensitiveDataMasker(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TraceContextProvider traceContextProvider() {
        return new DefaultTraceContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    LogEventFormatter logEventFormatter(LoggingProperties properties, ObjectMapper objectMapper,
                                        SensitiveDataMasker masker, Environment environment) {
        return new DefaultLogEventFormatter(properties, objectMapper, masker,
                serviceName(properties, environment), environmentName(properties, environment));
    }

    @Bean
    @ConditionalOnMissingBean
    HttpLogWriter httpLogWriter(LogEventFormatter formatter) {
        return new Slf4jHttpLogWriter(formatter);
    }

    @Bean
    @ConditionalOnMissingBean
    LoggingService loggingService(LoggingProperties properties, LogEventFormatter formatter) {
        return new DefaultLoggingService(properties, formatter);
    }

    @Bean
    @ConditionalOnMissingBean
    AuditLogger auditLogger(LoggingService loggingService) {
        return new AuditLogger(loggingService);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HttpServletRequest.class)
    @ConditionalOnMissingBean
    ClientIpResolver clientIpResolver(LoggingProperties properties) {
        return new DefaultClientIpResolver(properties);
    }

    @Bean
    @ConditionalOnClass(Authentication.class)
    @ConditionalOnMissingBean(UserIdentityProvider.class)
    UserIdentityProvider springSecurityUserIdentityProvider(LoggingProperties properties) {
        return new DefaultUserIdentityProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean({UserIdentityProvider.class, Authentication.class})
    UserIdentityProvider anonymousUserIdentityProvider() {
        return new AnonymousUserIdentityProvider();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HttpServletRequest.class)
    @ConditionalOnMissingBean
    OperationResolver operationResolver(LoggingProperties properties) {
        return new DefaultOperationResolver(properties);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HttpServletRequest.class)
    @ConditionalOnMissingBean
    OperationLoggingInterceptor operationLoggingInterceptor() {
        return new OperationLoggingInterceptor();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HttpServletRequest.class)
    WebMvcConfigurer loggingWebMvcConfigurer(OperationLoggingInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HttpServletRequest.class)
    @ConditionalOnMissingBean(name = "httpLoggingFilterRegistration")
    FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilterRegistration(
            LoggingProperties properties,
            LoggingContextManager contextManager,
            SensitiveDataMasker masker,
            ClientIpResolver clientIpResolver,
            UserIdentityProvider userIdentityProvider,
            TraceContextProvider traceContextProvider,
            OperationResolver operationResolver,
            HttpLogWriter httpLogWriter,
            Environment environment) {
        HttpLoggingFilter filter = new HttpLoggingFilter(properties, contextManager, masker, clientIpResolver,
                userIdentityProvider, traceContextProvider, operationResolver, httpLogWriter,
                serviceName(properties, environment), environmentName(properties, environment));
        FilterRegistrationBean<HttpLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setName("httpLoggingFilter");
        return registration;
    }

    static String serviceName(LoggingProperties properties, Environment environment) {
        if (properties.getServiceName() != null && !properties.getServiceName().isBlank()) {
            return properties.getServiceName();
        }
        String applicationName = environment.getProperty("spring.application.name");
        return applicationName == null || applicationName.isBlank() ? "unknown-service" : applicationName;
    }

    static String environmentName(LoggingProperties properties, Environment environment) {
        if (properties.getEnvironment() != null && !properties.getEnvironment().isBlank()) {
            return properties.getEnvironment();
        }
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", Arrays.asList(profiles));
    }
}
