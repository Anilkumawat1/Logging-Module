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
        // Pre-snapshot: CachedBodyHttpServletRequest has bytes immediately available.
        // ContentCachingRequestWrapper only fills its buffer after the chain reads the stream,
        // so we snapshot again after the chain in the finally block.
        byte[] preChainPayload = snapshotRequestPayload(requestWrapper);
        try {
            writeRequestEvent(requestWrapper, preChainPayload);
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                // Re-snapshot after chain: picks up bytes from ContentCachingRequestWrapper
                // that were only populated after the request body was read.
                byte[] postChainPayload = snapshotRequestPayload(requestWrapper);
                byte[] resolvedPayload = postChainPayload.length > 0 ? postChainPayload : preChainPayload;
                writeResponseEvent(requestWrapper, responseWrapper, start, failure, resolvedPayload);
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
        // Note: type is NOT set in MDC for regular application logs.
        // - APPLICATION is the default/implicit type for any log.info() call — adding it
        //   to every line is noise with zero signal. The 'logger' field already classifies the source.
        // - HTTP_REQUEST / HTTP_RESPONSE are set via the event model in writeRequestEvent/writeResponseEvent.
        // - BUSINESS / AUDIT / SECURITY etc. are set explicitly by LoggingService when those events are logged.
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

    private void writeRequestEvent(HttpServletRequest request, byte[] snapshotedPayload) {
        UserIdentity identity = safeUser().orElse(UserIdentity.anonymous());
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
        // Print request payload in the request event unconditionally (if globally enabled).
        // It will ALSO be printed in the response event conditionally based on on-status-ranges.
        if (shouldLogRequestPayload(request) && snapshotedPayload != null && snapshotedPayload.length > 0) {
            event.requestPayload(payload(snapshotedPayload, request.getCharacterEncoding(), properties.getPayload().getRequestMaxSize()));
        }
        httpLogWriter.write(event);
    }

    private void writeResponseEvent(HttpServletRequest request, ContentCachingResponseWrapper response,
                                    long start, Throwable failure, byte[] snapshotedRequestPayload) {
        int status = response.getStatus();
        UserIdentity identity = safeUser().orElse(UserIdentity.anonymous());
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
                && snapshotedRequestPayload != null && snapshotedRequestPayload.length > 0) {
            event.requestPayload(payload(snapshotedRequestPayload, request.getCharacterEncoding(), properties.getPayload().getRequestMaxSize()));
        }
        if (shouldLogResponsePayload(response)) {
            event.responsePayload(payload(response.getContentAsByteArray(), response.getCharacterEncoding(), properties.getPayload().getResponseMaxSize()));
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
                .traceId(MDC.get(MdcKeys.TRACE_ID))
                .spanId(MDC.get(MdcKeys.SPAN_ID))
                .method(request.getMethod())
                .endpoint(endpoint(request));
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
     * Snapshot the request body bytes eagerly (before the filter chain runs) so they
     * can be attached to both the request event and the response event.
     * Returns an empty array when payload logging is disabled or the content type is excluded.
     */
    private byte[] snapshotRequestPayload(HttpServletRequest request) {
        if (!shouldLogRequestPayload(request)) {
            return new byte[0];
        }
        if (request instanceof CachedBodyHttpServletRequest cached) {
            return cached.getCachedBody();
        }
        // For ContentCachingRequestWrapper the body is only available after the
        // chain reads the stream; return empty here and it will be filled after chain.
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
