package com.anil.logging.config;

import com.anil.logging.model.LogCategory;
import com.anil.logging.model.LogFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "app.logging")
public class LoggingProperties {
    private boolean enabled = true;
    private String serviceName;
    private String environment;
    private LogFormat format = LogFormat.JSON;
    private Include include = new Include();
    private Payload payload = new Payload();
    private RequestPayload requestPayload = new RequestPayload();
    private Masking masking = new Masking();
    private Ip ip = new Ip();
    private Excluded excluded = new Excluded();
    private Operations operations = new Operations();
    private Map<LogCategory, Boolean> categories = defaultCategories();
    private Levels levels = new Levels();
    private Context context = new Context();
    private Async async = new Async();
    private Identity identity = new Identity();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public LogFormat getFormat() {
        return format;
    }

    public void setFormat(LogFormat format) {
        this.format = format;
    }

    public Include getInclude() {
        return include;
    }

    public void setInclude(Include include) {
        this.include = include;
    }

    public Payload getPayload() {
        return payload;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    public RequestPayload getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(RequestPayload requestPayload) {
        this.requestPayload = requestPayload;
    }

    public Masking getMasking() {
        return masking;
    }

    public void setMasking(Masking masking) {
        this.masking = masking;
    }

    public Ip getIp() {
        return ip;
    }

    public void setIp(Ip ip) {
        this.ip = ip;
    }

    public Excluded getExcluded() {
        return excluded;
    }

    public void setExcluded(Excluded excluded) {
        this.excluded = excluded;
    }

    public Operations getOperations() {
        return operations;
    }

    public void setOperations(Operations operations) {
        this.operations = operations;
    }

    public Map<LogCategory, Boolean> getCategories() {
        return categories;
    }

    public void setCategories(Map<LogCategory, Boolean> categories) {
        this.categories = categories;
    }

    public Levels getLevels() {
        return levels;
    }

    public void setLevels(Levels levels) {
        this.levels = levels;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async;
    }

    public Identity getIdentity() {
        return identity;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public boolean categoryEnabled(LogCategory category) {
        return categories.getOrDefault(category, Boolean.TRUE);
    }

    private static Map<LogCategory, Boolean> defaultCategories() {
        Map<LogCategory, Boolean> result = new EnumMap<>(LogCategory.class);
        for (LogCategory category : LogCategory.values()) {
            result.put(category, Boolean.TRUE);
        }
        return result;
    }

    public static class Include {
        private boolean requestHeaders = false;
        private boolean responseHeaders = false;
        private boolean requestParameters = true;
        private boolean requestPayload = true;
        private boolean responsePayload = false;
        private boolean user = true;
        private boolean ip = true;
        private boolean domain = true;
        private boolean url = true;
        private boolean operation = true;
        private boolean trace = true;
        private boolean thread = true;
        private boolean logger = true;
        private boolean caller = false;

        public boolean isRequestHeaders() {
            return requestHeaders;
        }

        public void setRequestHeaders(boolean requestHeaders) {
            this.requestHeaders = requestHeaders;
        }

        public boolean isResponseHeaders() {
            return responseHeaders;
        }

        public void setResponseHeaders(boolean responseHeaders) {
            this.responseHeaders = responseHeaders;
        }

        public boolean isRequestParameters() {
            return requestParameters;
        }

        public void setRequestParameters(boolean requestParameters) {
            this.requestParameters = requestParameters;
        }

        public boolean isRequestPayload() {
            return requestPayload;
        }

        public void setRequestPayload(boolean requestPayload) {
            this.requestPayload = requestPayload;
        }

        public boolean isResponsePayload() {
            return responsePayload;
        }

        public void setResponsePayload(boolean responsePayload) {
            this.responsePayload = responsePayload;
        }

        public boolean isUser() {
            return user;
        }

        public void setUser(boolean user) {
            this.user = user;
        }

        public boolean isIp() {
            return ip;
        }

        public void setIp(boolean ip) {
            this.ip = ip;
        }

        public boolean isDomain() {
            return domain;
        }

        public void setDomain(boolean domain) {
            this.domain = domain;
        }

        public boolean isUrl() {
            return url;
        }

        public void setUrl(boolean url) {
            this.url = url;
        }

        public boolean isOperation() {
            return operation;
        }

        public void setOperation(boolean operation) {
            this.operation = operation;
        }

        public boolean isTrace() {
            return trace;
        }

        public void setTrace(boolean trace) {
            this.trace = trace;
        }

        public boolean isThread() {
            return thread;
        }

        public void setThread(boolean thread) {
            this.thread = thread;
        }

        public boolean isLogger() {
            return logger;
        }

        public void setLogger(boolean logger) {
            this.logger = logger;
        }

        public boolean isCaller() {
            return caller;
        }

        public void setCaller(boolean caller) {
            this.caller = caller;
        }
    }

    public static class Payload {
        private int requestMaxSize = 10_000;
        private int responseMaxSize = 10_000;
        private boolean logBinary = false;

        public int getRequestMaxSize() {
            return requestMaxSize;
        }

        public void setRequestMaxSize(int requestMaxSize) {
            this.requestMaxSize = requestMaxSize;
        }

        public int getResponseMaxSize() {
            return responseMaxSize;
        }

        public void setResponseMaxSize(int responseMaxSize) {
            this.responseMaxSize = responseMaxSize;
        }

        public boolean isLogBinary() {
            return logBinary;
        }

        public void setLogBinary(boolean logBinary) {
            this.logBinary = logBinary;
        }
    }

    public static class RequestPayload {
        private List<String> onStatusRanges = new ArrayList<>(List.of("100-599"));

        public List<String> getOnStatusRanges() {
            return onStatusRanges;
        }

        public void setOnStatusRanges(List<String> onStatusRanges) {
            this.onStatusRanges = onStatusRanges;
        }
    }

    public static class Masking {
        private boolean enabled = true;
        private String replacement = "***";
        private List<String> fields = new ArrayList<>(List.of(
                "password", "passwd", "pwd", "token", "accessToken", "access_token", "refreshToken",
                "refresh_token", "idToken", "id_token", "secret", "apiKey", "api_key", "clientSecret",
                "client_secret", "authorization", "otp", "cvv", "cvc", "cardNumber", "card_number",
                "privateKey", "private_key", "secretKey", "secret_key"));
        private List<String> headers = new ArrayList<>(List.of(
                "authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token", "proxy-authorization"));
        private List<String> queryParameters = new ArrayList<>(List.of(
                "token", "password", "secret", "apiKey", "api_key", "accessToken", "access_token",
                "refreshToken", "refresh_token", "clientSecret", "client_secret", "otp"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getReplacement() {
            return replacement;
        }

        public void setReplacement(String replacement) {
            this.replacement = replacement;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public List<String> getQueryParameters() {
            return queryParameters;
        }

        public void setQueryParameters(List<String> queryParameters) {
            this.queryParameters = queryParameters;
        }

        public Set<String> allSensitiveFieldNames() {
            Set<String> values = new LinkedHashSet<>();
            values.addAll(fields);
            values.addAll(headers);
            values.addAll(queryParameters);
            return values;
        }
    }

    public static class Ip {
        private List<String> headers = new ArrayList<>(List.of("X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP"));

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }
    }

    public static class Excluded {
        private List<String> paths = new ArrayList<>(List.of("/actuator/health", "/actuator/prometheus"));
        private List<String> methods = new ArrayList<>(List.of("OPTIONS"));
        private List<String> contentTypes = new ArrayList<>(List.of(
                "multipart/form-data", "application/octet-stream", "application/pdf",
                "image/", "video/", "audio/"));

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public List<String> getContentTypes() {
            return contentTypes;
        }

        public void setContentTypes(List<String> contentTypes) {
            this.contentTypes = contentTypes;
        }
    }

    public static class Operations {
        private Map<String, String> mappings = new LinkedHashMap<>();

        public Map<String, String> getMappings() {
            return mappings;
        }

        public void setMappings(Map<String, String> mappings) {
            this.mappings = mappings;
        }
    }

    public static class Levels {
        private String success = "INFO";
        private String clientError = "WARN";
        private String serverError = "ERROR";

        public String getSuccess() {
            return success;
        }

        public void setSuccess(String success) {
            this.success = success;
        }

        public String getClientError() {
            return clientError;
        }

        public void setClientError(String clientError) {
            this.clientError = clientError;
        }

        public String getServerError() {
            return serverError;
        }

        public void setServerError(String serverError) {
            this.serverError = serverError;
        }
    }

    public static class Context {
        private Fields fields = new Fields();
        private Propagation propagation = new Propagation();

        public Fields getFields() {
            return fields;
        }

        public void setFields(Fields fields) {
            this.fields = fields;
        }

        public Propagation getPropagation() {
            return propagation;
        }

        public void setPropagation(Propagation propagation) {
            this.propagation = propagation;
        }
    }

    public static class Fields {
        private boolean requestId = true;
        private boolean traceId = true;
        private boolean spanId = true;
        private boolean userId = true;
        private boolean roles = true;
        private boolean service = true;
        private boolean environment = true;

        public boolean isRequestId() {
            return requestId;
        }

        public void setRequestId(boolean requestId) {
            this.requestId = requestId;
        }

        public boolean isTraceId() {
            return traceId;
        }

        public void setTraceId(boolean traceId) {
            this.traceId = traceId;
        }

        public boolean isSpanId() {
            return spanId;
        }

        public void setSpanId(boolean spanId) {
            this.spanId = spanId;
        }

        public boolean isUserId() {
            return userId;
        }

        public void setUserId(boolean userId) {
            this.userId = userId;
        }

        public boolean isRoles() {
            return roles;
        }

        public void setRoles(boolean roles) {
            this.roles = roles;
        }

        public boolean isService() {
            return service;
        }

        public void setService(boolean service) {
            this.service = service;
        }

        public boolean isEnvironment() {
            return environment;
        }

        public void setEnvironment(boolean environment) {
            this.environment = environment;
        }
    }

    public static class Propagation {
        private boolean enabled = true;
        private List<String> allowedFields = new ArrayList<>(List.of(
                "request_id", "trace_id", "span_id", "user_id", "role_ids", "authenticated",
                "service", "environment", "tenant_id", "domain", "operation", "type"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedFields() {
            return allowedFields;
        }

        public void setAllowedFields(List<String> allowedFields) {
            this.allowedFields = allowedFields;
        }
    }

    public static class Async {
        private boolean enabled = true;
        private boolean taskDecorator = true;
        private boolean executorIntegration = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isTaskDecorator() {
            return taskDecorator;
        }

        public void setTaskDecorator(boolean taskDecorator) {
            this.taskDecorator = taskDecorator;
        }

        public boolean isExecutorIntegration() {
            return executorIntegration;
        }

        public void setExecutorIntegration(boolean executorIntegration) {
            this.executorIntegration = executorIntegration;
        }
    }

    public static class Identity {
        private List<String> userIdClaims = new ArrayList<>(List.of("user_id", "userId", "sub"));
        private List<String> roleIdClaims = new ArrayList<>(List.of("role_ids", "roleIds", "roles", "authorities", "scope", "scp"));

        public List<String> getUserIdClaims() {
            return userIdClaims;
        }

        public void setUserIdClaims(List<String> userIdClaims) {
            this.userIdClaims = userIdClaims;
        }

        public List<String> getRoleIdClaims() {
            return roleIdClaims;
        }

        public void setRoleIdClaims(List<String> roleIdClaims) {
            this.roleIdClaims = roleIdClaims;
        }
    }

    public static boolean contentTypeMatches(String actual, Iterable<String> configured) {
        if (actual == null) {
            return false;
        }
        String normalized = actual.toLowerCase(Locale.ROOT);
        for (String candidate : configured) {
            String value = candidate == null ? "" : candidate.toLowerCase(Locale.ROOT);
            if (value.endsWith("/") && normalized.startsWith(value)) {
                return true;
            }
            if (normalized.startsWith(value)) {
                return true;
            }
        }
        return false;
    }
}
