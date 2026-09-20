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
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
        if (!properties.isEnabled()
                || (!properties.categoryEnabled(LogCategory.HTTP_REQUEST)
                && !properties.categoryEnabled(LogCategory.HTTP_RESPONSE))) {
            return true;
        }
        if (containsIgnoreCase(properties.getExcluded().getMethods(), request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return properties.getExcluded().getPaths().stream().anyMatch(path -> pathMatches(path, uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        LoggingContextSnapshot previous = contextManager.captureRaw();
        long start = System.nanoTime();
        Throwable failure = null;
        HttpServletRequest requestWrapper = wrapRequestForLogging(request);
        HttpServletResponse responseWrapper = wrapResponseForLogging(response);
        UserIdentity identity = installMdc(requestWrapper);
        try {
            writeRequestEventSafely(requestWrapper, identity);
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                if (requestWrapper.isAsyncStarted()) {
                    registerAsyncCompletion(requestWrapper, responseWrapper, start, identity);
                } else {
                    writeResponseEventSafely(requestWrapper, responseWrapper, start, failure, identity);
                }
            } finally {
                contextManager.restoreRaw(previous);
            }
        }
    }

    private UserIdentity installMdc(HttpServletRequest request) {
        TraceContext trace = resolveTrace(request);
        removeManagedMdcKeys();
        String requestId = firstNonBlank(request.getHeader(REQUEST_ID_HEADER), request.getHeader(CORRELATION_ID_HEADER), UUID.randomUUID().toString());
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        MDC.put(MdcKeys.SERVICE, serviceName);
        MDC.put(MdcKeys.ENVIRONMENT, environment);
        // Note: type is NOT set in MDC for regular application logs.
        // - APPLICATION is the default/implicit type for any log.info() call — adding it
        //   to every line is noise with zero signal. The 'logger' field already classifies the source.
        // - HTTP_REQUEST / HTTP_RESPONSE are set via the event model in writeRequestEvent/writeResponseEvent.
        // - BUSINESS / AUDIT / SECURITY etc. are set explicitly by LoggingService when those events are logged.
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
        return identity;
    }

    private void removeManagedMdcKeys() {
        MDC.remove(MdcKeys.REQUEST_ID);
        MDC.remove(MdcKeys.TRACE_ID);
        MDC.remove(MdcKeys.SPAN_ID);
        MDC.remove(MdcKeys.USER_ID);
        MDC.remove(MdcKeys.ROLES);
        MDC.remove(MdcKeys.AUTHENTICATED);
        MDC.remove(MdcKeys.SERVICE);
        MDC.remove(MdcKeys.ENVIRONMENT);
        MDC.remove(MdcKeys.TYPE);
        MDC.remove(MdcKeys.OPERATION);
    }

    private HttpServletRequest wrapRequestForLogging(HttpServletRequest request) {
        if (!shouldCaptureRequestPayload(request)) {
            return request;
        }
        return new ContentCachingRequestWrapper(request, Math.max(properties.getPayload().getRequestMaxSize(), 0));
    }

    private boolean shouldCaptureRequestPayload(HttpServletRequest request) {
        return properties.getInclude().isRequestPayload()
                && contentCanBeLogged(request.getContentType())
                && properties.categoryEnabled(LogCategory.HTTP_RESPONSE);
    }

    private HttpServletResponse wrapResponseForLogging(HttpServletResponse response) {
        if (!properties.getInclude().isResponsePayload()
                || !properties.categoryEnabled(LogCategory.HTTP_RESPONSE)) {
            return response;
        }
        return new BoundedContentCachingResponseWrapper(response, properties.getPayload().getResponseMaxSize());
    }

    private void writeRequestEventSafely(HttpServletRequest request, UserIdentity identity) {
        if (!properties.categoryEnabled(LogCategory.HTTP_REQUEST)) {
            return;
        }
        try {
            writeRequestEvent(request, identity);
        } catch (RuntimeException ex) {
            logger.warn("HTTP request logging failed safely: " + ex);
        }
    }

    private void writeRequestEvent(HttpServletRequest request, UserIdentity identity) {
        HttpLogEvent event = baseHttpEvent(request, identity);
        event.setType(LogCategory.HTTP_REQUEST);
        event.setLevel(properties.getLevels().getSuccess());
        // Operation is intentionally omitted on the request event: the DispatcherServlet
        // hasn't run yet, so HandlerMapping attributes and @LogOperation are not set.
        // Operation is resolved accurately only on the response event.
        event.setMessage("HTTP request " + request.getMethod() + " " + endpoint(request));
        if (properties.getInclude().isRequestHeaders()) {
            event.requestHeaders(masker.maskMap(headers(request), MaskingTarget.HEADER));
        }
        if (properties.getInclude().isRequestParameters()) {
            event.requestParameters(masker.maskMap(parameters(request), MaskingTarget.QUERY_PARAMETER));
        }
        httpLogWriter.write(event);
    }

    private void writeResponseEventSafely(HttpServletRequest request, HttpServletResponse response,
                                          long start, Throwable failure, UserIdentity identity) {
        if (!properties.categoryEnabled(LogCategory.HTTP_RESPONSE)) {
            return;
        }
        try {
            writeResponseEvent(request, response, start, failure, snapshotRequestPayload(request), identity);
        } catch (RuntimeException ex) {
            logger.warn("HTTP response logging failed safely: " + ex);
        }
    }

    private void writeResponseEvent(HttpServletRequest request, HttpServletResponse response,
                                    long start, Throwable failure, byte[] snapshottedRequestPayload,
                                    UserIdentity identity) {
        int actualStatus = response.getStatus();
        int status = failure != null && actualStatus < 400 && !response.isCommitted() ? 500 : actualStatus;
        HttpLogEvent event = baseHttpEvent(request, identity);
        event.setType(LogCategory.HTTP_RESPONSE);
        event.setLevel(levelFor(status, failure));
        // Message carries status code so a quick grep on logs shows success/error at a glance.
        // method, endpoint, and operation are already structured fields below.
        event.setMessage("HTTP response " + status + " " + request.getMethod() + " " + endpoint(request));
        event.setThrowable(failure);
        event.responseStatus(status)
                .responseTimeMs((System.nanoTime() - start) / 1_000_000);
        // Operation is resolved here (after the chain) where both HandlerMapping attributes
        // and @LogOperation annotation attributes are fully populated.
        if (properties.getInclude().isOperation()) {
            event.operation(safeOperation(request));
        }
        if (properties.getInclude().isResponseHeaders()) {
            event.responseHeaders(masker.maskMap(responseHeaders(response), MaskingTarget.HEADER));
        }
        // Include the request payload on the response event only when the status falls
        // within the configured on-status-ranges (default 100-599 = always).
        // This lets you say "only capture request body on errors (400-599)" without
        // logging it on every successful request.
        if (shouldLogRequestPayloadForResponse(request, status)
                && snapshottedRequestPayload != null && snapshottedRequestPayload.length > 0) {
            event.requestPayload(payload(snapshottedRequestPayload, request.getCharacterEncoding(), properties.getPayload().getRequestMaxSize()));
        }
        if (shouldLogResponsePayload(response) && response instanceof BoundedContentCachingResponseWrapper cached) {
            String responsePayload = payload(cached.getCapturedContent(), response.getCharacterEncoding(),
                    properties.getPayload().getResponseMaxSize());
            if (responsePayload != null && cached.isCaptureTruncated()) {
                responsePayload += "...[truncated]";
            }
            event.responsePayload(responsePayload);
        }
        httpLogWriter.write(event);
    }

    /**
     * Builds the common fields shared by both request and response log events.
     * Note: {@code operation} is intentionally NOT set here — it must be resolved
     * post-chain (in {@link #writeResponseEvent}) where Spring MVC handler-mapping
     * attributes and {@code @LogOperation} have been populated.
     */
    private HttpLogEvent baseHttpEvent(HttpServletRequest request, UserIdentity identity) {
        HttpLogEvent event = new HttpLogEvent();
        event.requestId(MDC.get(MdcKeys.REQUEST_ID))
                .method(request.getMethod())
                .endpoint(endpoint(request));
        if (properties.getInclude().isTrace()) {
            event.traceId(MDC.get(MdcKeys.TRACE_ID));
            event.spanId(MDC.get(MdcKeys.SPAN_ID));
        }
        if (properties.getInclude().isUser()) {
            event.authenticated(identity.authenticated());
            // Only include userId and roles for authenticated users — avoids logging
            // "user_id": null and "role_ids": [] noise for anonymous/public requests.
            if (identity.authenticated()) {
                event.userId(identity.userId());
                if (identity.roles() != null && !identity.roles().isEmpty()) {
                    event.roles(identity.roles());
                }
            }
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

    /**
     * Whether to include the request payload in the <em>response</em> log event.
     * <p>
     * This adds the {@code request-payload.on-status-ranges} gate on top of the
     * base content-type / enabled check. Example config:
     * <pre>
     * app.logging.request-payload.on-status-ranges:
     *   - "400-599"   # only log request body when something went wrong
     * </pre>
     * Default range is {@code 100-599} (always log, preserving existing behaviour).
     */
    private boolean shouldLogRequestPayloadForResponse(HttpServletRequest request, int status) {
        if (!shouldLogRequestPayload(request)) {
            return false;
        }
        List<String> ranges = properties.getRequestPayload().getOnStatusRanges();
        if (ranges == null || ranges.isEmpty()) {
            return true;
        }
        return isStatusInAnyRange(status, ranges);
    }

    private static boolean isStatusInAnyRange(int status, List<String> ranges) {
        for (String range : ranges) {
            if (range == null) continue;
            String trimmed = range.trim();
            int dash = trimmed.indexOf('-');
            try {
                if (dash < 0) {
                    // Single exact status, e.g. "422"
                    if (status == Integer.parseInt(trimmed)) return true;
                } else {
                    int low  = Integer.parseInt(trimmed.substring(0, dash).trim());
                    int high = Integer.parseInt(trimmed.substring(dash + 1).trim());
                    if (status >= low && status <= high) return true;
                }
            } catch (NumberFormatException ignored) {
                // Malformed range entry — skip silently, don't blow up request logging
            }
        }
        return false;
    }

    /**
     * Returns the bounded bytes passively cached while the application consumed the request.
     * No request body is read by the logging filter itself.
     */
    private byte[] snapshotRequestPayload(HttpServletRequest request) {
        if (!shouldLogRequestPayload(request)) {
            return new byte[0];
        }
        if (request instanceof ContentCachingRequestWrapper cached) {
            return cached.getContentAsByteArray();
        }
        return new byte[0];
    }

    private boolean shouldLogResponsePayload(HttpServletResponse response) {
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
        Charset charset;
        try {
            charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        } catch (RuntimeException ignored) {
            charset = StandardCharsets.UTF_8;
        }
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
        return request.getRequestURL().toString();
    }

    private void registerAsyncCompletion(HttpServletRequest request, HttpServletResponse response,
                                         long start, UserIdentity identity) {
        try {
            LoggingContextSnapshot requestContext = contextManager.capture();
            request.getAsyncContext().addListener(
                    new AsyncLoggingListener(request, response, start, identity, requestContext));
        } catch (RuntimeException ex) {
            logger.warn("Could not register async HTTP logging completion listener: " + ex);
        }
    }

    private final class AsyncLoggingListener implements AsyncListener {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final long start;
        private final UserIdentity identity;
        private final LoggingContextSnapshot requestContext;
        private final AtomicBoolean written = new AtomicBoolean();
        private volatile Throwable failure;

        private AsyncLoggingListener(HttpServletRequest request, HttpServletResponse response, long start,
                                     UserIdentity identity, LoggingContextSnapshot requestContext) {
            this.request = request;
            this.response = response;
            this.start = start;
            this.identity = identity;
            this.requestContext = requestContext;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            writeOnce();
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            failure = new TimeoutException("Servlet async request timed out");
            writeOnce();
        }

        @Override
        public void onError(AsyncEvent event) {
            failure = event.getThrowable();
            writeOnce();
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }

        private void writeOnce() {
            if (!written.compareAndSet(false, true)) {
                return;
            }
            LoggingContextSnapshot previous = contextManager.captureRaw();
            try {
                contextManager.restore(requestContext);
                writeResponseEventSafely(request, response, start, failure, identity);
            } finally {
                contextManager.restoreRaw(previous);
            }
        }
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

    private static boolean pathMatches(String configuredPath, String uri) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return false;
        }
        if (configuredPath.equals(uri)) {
            return true;
        }
        if (!configuredPath.endsWith("/**")) {
            return false;
        }
        String base = configuredPath.substring(0, configuredPath.length() - 3);
        return uri.equals(base) || uri.startsWith(base + "/");
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
