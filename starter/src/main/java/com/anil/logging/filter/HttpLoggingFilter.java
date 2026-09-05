package com.anil.logging.filter;

import com.anil.logging.config.LoggingProperties;
import com.anil.logging.context.LoggingContextSnapshot;
import com.anil.logging.context.LoggingContextManager;
import com.anil.logging.context.MdcKeys;
import com.anil.logging.logging.HttpLogWriter;
import com.anil.logging.masking.MaskingTarget;
import com.anil.logging.masking.SensitiveDataMasker;
import com.anil.logging.model.HttpLogEvent;
import com.anil.logging.model.LogCategory;
import com.anil.logging.operation.OperationResolver;
import com.anil.logging.security.UserIdentity;
import com.anil.logging.security.UserIdentityProvider;
import com.anil.logging.trace.TraceContext;
import com.anil.logging.trace.TraceContextProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class HttpLoggingFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final LoggingProperties properties;
    private final LoggingContextManager contextManager;
    private final SensitiveDataMasker masker;
    private final ClientIpResolver clientIpResolver;
    private final UserIdentityProvider userIdentityProvider;
    private final TraceContextProvider traceContextProvider;
    private final OperationResolver operationResolver;
    private final HttpLogWriter httpLogWriter;
    private final String serviceName;
    private final String environment;

    public HttpLoggingFilter(LoggingProperties properties,
                             LoggingContextManager contextManager,
                             SensitiveDataMasker masker,
                             ClientIpResolver clientIpResolver,
                             UserIdentityProvider userIdentityProvider,
                             TraceContextProvider traceContextProvider,
                             OperationResolver operationResolver,
                             HttpLogWriter httpLogWriter,
                             String serviceName,
                             String environment) {
        this.properties = properties;
        this.contextManager = contextManager;
        this.masker = masker;
        this.clientIpResolver = clientIpResolver;
        this.userIdentityProvider = userIdentityProvider;
        this.traceContextProvider = traceContextProvider;
        this.operationResolver = operationResolver;
        this.httpLogWriter = httpLogWriter;
        this.serviceName = serviceName;
        this.environment = environment;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled() || !properties.categoryEnabled(LogCategory.HTTP_REQUEST)) {
            return true;
        }
        if (containsIgnoreCase(properties.getExcluded().getMethods(), request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return properties.getExcluded().getPaths().stream().anyMatch(path -> path.equals(uri) || (path.endsWith("/**") && uri.startsWith(path.substring(0, path.length() - 3))));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        LoggingContextSnapshot previous = contextManager.capture();
        long start = System.nanoTime();
        Throwable failure = null;
        HttpServletRequest requestWrapper = wrapRequestForLogging(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        installMdc(requestWrapper);
        try {
            writeRequestEvent(requestWrapper);
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                writeResponseEvent(requestWrapper, responseWrapper, start, failure);
            } catch (RuntimeException ex) {
                logger.warn("HTTP logging failed safely: " + ex);
            } finally {
                responseWrapper.copyBodyToResponse();
                contextManager.restore(previous);
            }
        }
    }

    private void installMdc(HttpServletRequest request) {
        String requestId = firstNonBlank(request.getHeader(REQUEST_ID_HEADER), request.getHeader(CORRELATION_ID_HEADER), UUID.randomUUID().toString());
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        MDC.put(MdcKeys.SERVICE, serviceName);
        MDC.put(MdcKeys.ENVIRONMENT, environment);
        MDC.put(MdcKeys.TYPE, LogCategory.APPLICATION.name());
        TraceContext trace = resolveTrace(request);
        if (trace.traceId() != null) {
            MDC.put(MdcKeys.TRACE_ID, trace.traceId());
        }
        if (trace.spanId() != null) {
            MDC.put(MdcKeys.SPAN_ID, trace.spanId());
        }
        Optional<UserIdentity> user = safeUser();
        UserIdentity identity = user.orElse(UserIdentity.anonymous());
        MDC.put(MdcKeys.AUTHENTICATED, String.valueOf(identity.authenticated()));
        if (identity.userId() != null) {
            MDC.put(MdcKeys.USER_ID, identity.userId());
        }
        if (identity.roles() != null && !identity.roles().isEmpty()) {
            MDC.put(MdcKeys.ROLES, identity.roles().toString());
        }
    }

    private HttpServletRequest wrapRequestForLogging(HttpServletRequest request) throws IOException {
        if (!shouldCacheRequestPayloadForRequestLog(request)) {
            return new ContentCachingRequestWrapper(request, properties.getPayload().getRequestMaxSize());
        }
        return new CachedBodyHttpServletRequest(request);
    }

    private boolean shouldCacheRequestPayloadForRequestLog(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        return properties.getInclude().isRequestPayload()
                && contentCanBeLogged(request.getContentType())
                && contentLength > 0
                && contentLength <= properties.getPayload().getRequestMaxSize();
    }

    private void writeRequestEvent(HttpServletRequest request) {
        UserIdentity identity = safeUser().orElse(UserIdentity.anonymous());
        HttpLogEvent event = baseHttpEvent(request, identity);
        event.setType(LogCategory.HTTP_REQUEST);
        event.setLevel(properties.getLevels().getSuccess());
        event.setMessage("HTTP request " + request.getMethod() + " " + endpoint(request));
        if (properties.getInclude().isRequestHeaders()) {
            event.requestHeaders(masker.maskMap(headers(request), MaskingTarget.HEADER));
        }
        if (properties.getInclude().isRequestParameters()) {
            event.requestParameters(masker.maskMap(parameters(request), MaskingTarget.QUERY_PARAMETER));
        }
        if (shouldLogRequestPayload(request)) {
            event.requestPayload(payload(requestPayloadBytes(request), request.getCharacterEncoding(), properties.getPayload().getRequestMaxSize()));
        }
        httpLogWriter.write(event);
    }

    private void writeResponseEvent(HttpServletRequest request, ContentCachingResponseWrapper response,
                                    long start, Throwable failure) {
        int status = response.getStatus();
        UserIdentity identity = safeUser().orElse(UserIdentity.anonymous());
        HttpLogEvent event = baseHttpEvent(request, identity);
        event.setType(LogCategory.HTTP_RESPONSE);
        event.setLevel(levelFor(status, failure));
        event.setMessage("HTTP response " + request.getMethod() + " " + endpoint(request) + " " + status);
        event.setThrowable(failure);
        event.responseStatus(status)
                .responseTimeMs((System.nanoTime() - start) / 1_000_000);
        if (properties.getInclude().isResponseHeaders()) {
            event.responseHeaders(masker.maskMap(responseHeaders(response), MaskingTarget.HEADER));
        }
        if (shouldLogResponsePayload(response)) {
            event.responsePayload(payload(response.getContentAsByteArray(), response.getCharacterEncoding(), properties.getPayload().getResponseMaxSize()));
        }
        httpLogWriter.write(event);
    }

    private HttpLogEvent baseHttpEvent(HttpServletRequest request, UserIdentity identity) {
        HttpLogEvent event = new HttpLogEvent();
        event.requestId(MDC.get(MdcKeys.REQUEST_ID))
                .traceId(MDC.get(MdcKeys.TRACE_ID))
                .spanId(MDC.get(MdcKeys.SPAN_ID))
                .method(request.getMethod())
                .endpoint(endpoint(request))
                .operation(properties.getInclude().isOperation() ? safeOperation(request) : null);
        if (properties.getInclude().isUser()) {
            event.authenticated(identity.authenticated())
                    .userId(identity.userId())
                    .roles(identity.roles());
        }
        if (properties.getInclude().isIp()) {
            event.ip(safeIp(request));
        }
        if (properties.getInclude().isDomain()) {
            event.domain(request.getServerName());
        }
        if (properties.getInclude().isUrl()) {
            event.url(fullUrl(request));
        }
        return event;
    }

    private boolean shouldLogRequestPayload(HttpServletRequest request) {
        return properties.getInclude().isRequestPayload()
                && contentCanBeLogged(request.getContentType());
    }

    private byte[] requestPayloadBytes(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequest cached) {
            return cached.getCachedBody();
        }
        if (request instanceof ContentCachingRequestWrapper cached) {
            return cached.getContentAsByteArray();
        }
        return new byte[0];
    }

    private boolean shouldLogResponsePayload(ContentCachingResponseWrapper response) {
        return properties.getInclude().isResponsePayload() && contentCanBeLogged(response.getContentType());
    }

    private boolean contentCanBeLogged(String contentType) {
        return properties.getPayload().isLogBinary()
                || !LoggingProperties.contentTypeMatches(contentType, properties.getExcluded().getContentTypes());
    }

    private String payload(byte[] bytes, String encoding, int maxSize) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        int length = Math.min(bytes.length, Math.max(maxSize, 0));
        Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        String value = new String(bytes, 0, length, charset);
        String masked = masker.maskPayload(value);
        return bytes.length > length ? masked + "...[truncated]" : masked;
    }

    private TraceContext resolveTrace(HttpServletRequest request) {
        TraceContext current = traceContextProvider.currentTraceContext();
        if (current != null && current.hasTrace()) {
            return current;
        }
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 3) {
                return new TraceContext(parts[1], parts[2]);
            }
        }
        return new TraceContext(firstNonBlank(request.getHeader("X-B3-TraceId"), request.getHeader("X-Trace-Id")), request.getHeader("X-B3-SpanId"));
    }

    private Optional<UserIdentity> safeUser() {
        try {
            return userIdentityProvider.getCurrentUser();
        } catch (RuntimeException ex) {
            return Optional.of(UserIdentity.anonymous());
        }
    }

    private String safeIp(HttpServletRequest request) {
        try {
            return clientIpResolver.resolve(request);
        } catch (RuntimeException ex) {
            return request.getRemoteAddr();
        }
    }

    private String safeOperation(HttpServletRequest request) {
        try {
            return operationResolver.resolve(request);
        } catch (RuntimeException ex) {
            return endpoint(request);
        }
    }

    private static String endpoint(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? request.getRequestURI() : String.valueOf(pattern);
    }

    private static Map<String, ?> headers(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            result.put(name, Collections.list(request.getHeaders(name)));
        }
        return result;
    }

    private static Map<String, ?> responseHeaders(HttpServletResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        response.getHeaderNames().forEach(name -> result.put(name, response.getHeaders(name)));
        return result;
    }

    private static Map<String, ?> parameters(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> result.put(key, values.length == 1 ? values[0] : List.of(values)));
        return result;
    }

    private static String fullUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder(request.getRequestURL());
        if (request.getQueryString() != null) {
            url.append('?').append(request.getQueryString());
        }
        return url.toString();
    }

    private String levelFor(int status, Throwable failure) {
        if (failure != null || status >= 500) {
            return properties.getLevels().getServerError();
        }
        if (status >= 400) {
            return properties.getLevels().getClientError();
        }
        return properties.getLevels().getSuccess();
    }

    private static boolean containsIgnoreCase(Iterable<String> values, String needle) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
