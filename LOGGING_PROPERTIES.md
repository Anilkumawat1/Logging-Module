# Logging Properties and Use Cases

All configuration is under `app.logging`. Property names use Spring Boot's relaxed binding, so the YAML examples use kebab-case.

## Recommended production baseline

```yaml
app:
  logging:
    enabled: true
    format: json
    include:
      request-headers: false
      response-headers: false
      request-parameters: true
      request-payload: true
      response-payload: false
      user: true
      ip: true
      domain: true
      url: true
      operation: true
      trace: true
      thread: true
      logger: true
      caller: false
    payload:
      request-max-size: 10000
      response-max-size: 10000
      log-binary: false
    request-payload:
      on-status-ranges:
        - "400-599"
    masking:
      enabled: true
      replacement: "***"
    excluded:
      paths:
        - /actuator/health
        - /actuator/prometheus
      methods:
        - OPTIONS
    async:
      enabled: true
      task-decorator: true
      executor-integration: true
```

This baseline records request bodies only for failures, does not capture response bodies, and keeps expensive caller lookup disabled.

## General properties

| Property | Default | Purpose and use case |
|---|---:|---|
| `enabled` | `true` | Master switch for HTTP and `LoggingService` events. It does not disable ordinary application SLF4J logs. |
| `service-name` | `spring.application.name`, then `unknown-service` | Stable service identifier used by log search and dashboards. |
| `environment` | active profiles, then `default` | Deployment environment such as `dev`, `staging`, or `prod`. |
| `format` | `json` | `json` for production ingestion or `text` for local readability. Keep this aligned with the chosen Logback include. |

## HTTP field inclusion

| Property | Default | Effect |
|---|---:|---|
| `include.request-headers` | `false` | Adds masked request headers. Enable temporarily for integration diagnosis; headers can be large. |
| `include.response-headers` | `false` | Adds masked response headers. Useful for cache and rate-limit diagnosis. |
| `include.request-parameters` | `true` | Adds masked query/form parameters. The raw query string is never placed in `url`. |
| `include.request-payload` | `true` | Captures a bounded body while the application reads it. The body is emitted only on `HTTP_RESPONSE` when its status matches `request-payload.on-status-ranges`. |
| `include.response-payload` | `false` | Captures only a bounded prefix while bytes continue directly to the client. Enable selectively for small API responses. |
| `include.user` | `true` | Adds `authenticated`, `user_id`, and `role_ids` to HTTP events. |
| `include.ip` | `true` | Adds the resolved client IP. See the trusted-proxy warning below. |
| `include.domain` | `true` | Adds the request server name. |
| `include.url` | `true` | Adds scheme, host, port, and path without the query string. |
| `include.operation` | `true` | Adds the response operation resolved from annotation, mapping, or HTTP method. |
| `include.trace` | `true` | Adds trace and span IDs to HTTP events. Trace MDC remains available to ordinary application logs. |
| `include.thread` | `true` | Adds the current thread name to formatted structured events. |
| `include.logger` | `true` | Adds the structured/HTTP logger name. |
| `include.caller` | `false` | Performs a stack walk to add caller class, method, and line. Use only when its cost is acceptable. |

## Payload controls

| Property | Default | Effect |
|---|---:|---|
| `payload.request-max-size` | `10000` | Maximum request bytes retained for logging. The application can still read the complete request. Set `0` to retain none. |
| `payload.response-max-size` | `10000` | Maximum response bytes retained for logging. The client still receives the complete response. Set `0` to retain none. |
| `payload.log-binary` | `false` | Allows content types in the binary exclusion list. This is normally unsafe in production. |
| `request-payload.on-status-ranges` | `100-599` | Statuses that permit the captured request body in the final event. Entries can be ranges such as `400-599` or exact values such as `422`. |

If the application never consumes a request body, the passive request cache remains empty and no request payload is logged.

### Error-only request bodies

```yaml
app.logging.request-payload.on-status-ranges:
  - "400-599"
```

### Selected response bodies

The current switch applies to all non-excluded response content types:

```yaml
app.logging.include.response-payload: true
app.logging.payload.response-max-size: 4096
```

For endpoint-specific response policies, supply a custom `HttpLogWriter` or extend the starter with an endpoint policy SPI.

## Masking

| Property | Default | Effect |
|---|---:|---|
| `masking.enabled` | `true` | Enables masking consistently for fields, headers, parameters, payloads, nested structures, messages, and stack traces. Do not disable in production. |
| `masking.replacement` | `***` | Replacement written instead of a secret. |
| `masking.fields` | security-oriented list | Exact, case-insensitive JSON/map field names to mask. Replacing the list replaces defaults, so include every required default. |
| `masking.headers` | auth/cookie list | Exact, case-insensitive header names. Header names containing token, secret, authorization, API-key, or cookie are also treated as sensitive. |
| `masking.query-parameters` | token/password list | Exact, case-insensitive query or form parameter names to mask. |

Default field names include passwords, tokens, API/client secrets, card numbers, CVV/CVC, OTP, and private/secret keys. Default headers include authorization, cookie, set-cookie, API keys, auth tokens, and proxy authorization.

Application-specific example:

```yaml
app:
  logging:
    masking:
      fields:
        - password
        - token
        - national_id
        - bank_account
      headers:
        - authorization
        - cookie
        - x-partner-secret
      query-parameters:
        - token
        - password
        - authorization_code
```

Ordinary SLF4J messages are not intercepted. Never write secrets directly with `log.info`, `log.warn`, or `log.error`.

## Client IP

| Property | Default |
|---|---|
| `ip.headers` | `X-Forwarded-For`, `X-Real-IP`, `CF-Connecting-IP` |

The first non-blank configured header is used, with the first comma-separated value selected. Forwarded headers are client-controlled unless the service is behind a trusted proxy that overwrites them. Remove these headers from the list or provide a custom `ClientIpResolver` when that trust boundary does not exist.

## Exclusions

| Property | Default | Effect |
|---|---|---|
| `excluded.paths` | `/actuator/health`, `/actuator/prometheus` | Exact paths or boundary-aware patterns ending in `/**`. `/internal/**` matches `/internal` and `/internal/jobs`, but not `/internal-api`. |
| `excluded.methods` | `OPTIONS` | HTTP methods omitted from both request and response logging. Matching is case-insensitive. |
| `excluded.content-types` | multipart, octet-stream, PDF, image, video, audio | Payload content types that cannot be logged unless `payload.log-binary=true`. Metadata events are still written. |

```yaml
app.logging.excluded:
  paths:
    - /actuator/**
    - /internal/stream/**
  methods:
    - OPTIONS
    - HEAD
  content-types:
    - multipart/form-data
    - application/octet-stream
    - application/zip
    - image/
```

## Operations

`operations.mappings` maps `METHOD <best matching Spring MVC path>` to a business operation:

```yaml
app.logging.operations.mappings:
  "POST /api/orders": CREATE_ORDER
  "GET /api/orders/{id}": READ_ORDER
```

Resolution order is `@LogOperation`, property mapping, CRUD method fallback, then endpoint fallback. Operations are resolved after Spring MVC selects the handler, so they appear on response events.

## Categories and levels

Every category defaults to `true`:

- `HTTP_REQUEST`
- `HTTP_RESPONSE`
- `APPLICATION`
- `BUSINESS`
- `DATABASE`
- `INTEGRATION`
- `SECURITY`
- `PERFORMANCE`
- `AUDIT`
- `ERROR`
- `BACKGROUND_TASK`

Request and response categories operate independently:

```yaml
app.logging.categories:
  HTTP_REQUEST: false
  HTTP_RESPONSE: true
  AUDIT: true
  DATABASE: false
```

HTTP level selection:

| Property | Default | Used for |
|---|---:|---|
| `levels.success` | `INFO` | Status below 400 |
| `levels.client-error` | `WARN` | Status 400-499 |
| `levels.server-error` | `ERROR` | Status 500+, timeout, or unresolved exception |

Supported output levels are `TRACE`, `DEBUG`, `INFO`, `WARN`, and `ERROR`. Unknown values fall back to `INFO`.

## MDC output and propagation

`context.fields` controls standard MDC fields added by `DefaultLogEventFormatter`:

| Property | Default |
|---|---:|
| `context.fields.request-id` | `true` |
| `context.fields.trace-id` | `true` |
| `context.fields.span-id` | `true` |
| `context.fields.user-id` | `true` |
| `context.fields.roles` | `true` |
| `context.fields.service` | `true` |
| `context.fields.environment` | `true` |

`context.propagation.enabled` defaults to `true`. `context.propagation.allowed-fields` defaults to:

```text
request_id, trace_id, span_id, user_id, role_ids, authenticated,
service, environment, tenant_id, domain, operation, type
```

Only allowlisted, non-sensitive names move to another thread. Same-thread scope restoration always preserves the complete pre-existing MDC, including fields owned by tracing or other libraries.

```yaml
app.logging.context.propagation:
  enabled: true
  allowed-fields:
    - request_id
    - trace_id
    - span_id
    - user_id
    - service
    - environment
    - tenant_id
```

## Async integration

| Property | Default | Effect |
|---|---:|---|
| `async.enabled` | `true` | Enables logging context propagation infrastructure. |
| `async.task-decorator` | `true` | Creates `LoggingTaskDecorator` unless the application supplies one. |
| `async.executor-integration` | `true` | Applies the decorator to Spring Boot's application task executor. |

Custom executors still need `LoggingExecutors.wrap(...)` or an explicitly configured `LoggingTaskDecorator`. The JVM common pool is not automatically instrumented.

## Identity claims

| Property | Default |
|---|---|
| `identity.user-id-claims` | `user_id`, `userId`, `sub` |
| `identity.role-id-claims` | `role_ids`, `roleIds`, `roles`, `authorities`, `scope`, `scp` |

The first populated configured claim wins. Role claims accept collections, arrays, comma-separated text, or whitespace-separated OAuth scopes. If no role claim is present, Spring Security authorities are used.

```yaml
app.logging.identity:
  user-id-claims:
    - employee_id
    - sub
  role-id-claims:
    - groups
    - scope
```

The HTTP logging filter runs immediately downstream of Spring Security so authenticated identity is available to HTTP events and application MDC. Requests rejected entirely inside the security chain may require a Spring Security `AuthenticationEntryPoint` or `AccessDeniedHandler` security event if they must also be audited.

## Common use cases

### High-throughput API

Disable bodies, headers, and caller inspection:

```yaml
app.logging.include:
  request-headers: false
  response-headers: false
  request-parameters: true
  request-payload: false
  response-payload: false
  caller: false
```

### Troubleshooting only failed requests

```yaml
app.logging.include.request-payload: true
app.logging.request-payload.on-status-ranges:
  - "400-599"
```

### Privacy-sensitive service

```yaml
app.logging.include:
  request-headers: false
  response-headers: false
  request-parameters: false
  request-payload: false
  response-payload: false
  user: false
  ip: false
  url: false
```

### Local readable output

```yaml
app.logging.format: text
```

Use the bundled text Logback include in `logback-spring.xml`:

```xml
<configuration>
  <include resource="com/anil/logging/logback/logback-production-text.xml"/>
</configuration>
```
