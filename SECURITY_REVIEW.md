# Security Review

This starter is designed to make useful production logs without turning logs into a secret store.

## Defaults

- Authorization, cookies, API keys, auth tokens, passwords, client secrets, private keys, card numbers, CVV, CVC, and OTP values are masked by default.
- Header, query-parameter, and JSON field matching is case-insensitive.
- JSON masking is recursive across nested objects and arrays.
- Invalid JSON is handled through safe text masking instead of failing the request.
- Binary, multipart, PDF, image, video, and audio payloads are excluded by default.
- Payload capture is bounded by configurable request/response byte limits.
- HTTP request payload logging defaults to `400-599` responses.
- Logging failures are caught and do not fail the application request.
- MDC propagation uses an allowlist and a denylist. Sensitive names are not propagated even if someone puts them in MDC.

## Operational Guidance

- Treat forwarded IP headers as trustworthy only behind trusted proxies or load balancers.
- Avoid storing business event fields permanently in MDC. Use `LoggingContextScope` for bounded context.
- Keep audit events separate from normal HTTP logs.
- Do not enable binary payload logging in production unless the data classification has been reviewed.
- Review application-specific tokens, credentials, and identifiers and add them to `app.logging.masking.fields`, `headers`, or `query-parameters`.

## Residual Risks

- Exception messages can contain secrets created by application or third-party code; this starter masks known patterns and truncates messages, but teams should still avoid putting secrets in exception messages.
- JSON Logback output depends on the application's Logback setup. If an application uses a custom appender that omits MDC masking or logs raw request data elsewhere, that path must be reviewed separately.
