# Performance Review

The starter avoids expensive work unless the corresponding feature is enabled.

## Controls

- Request and response payload logging is bounded by `request-max-size` and `response-max-size`.
- Successful request payloads are omitted by default.
- Response payload logging is disabled by default.
- Binary and multipart payloads are skipped by default.
- Caller class/method stack inspection is disabled by default.
- Method-level timing through AOP is not enabled; explicit `TimerContext` is used instead.
- HTTP logging is one event per request, not multiple events around every filter stage.
- Context propagation captures a small allowlisted MDC map, not all process state.

## Tests Included

- Executor propagation
- Nested executor propagation
- `CompletableFuture` propagation with wrapped executor
- `@Async` propagation through Boot's task executor customizer
- Thread reuse and MDC cleanup
- Exception path cleanup
- Sensitive-field masking
- HTTP payload/header/query masking and binary omission

## Benchmark Guidance

For high-throughput services, benchmark with representative payload sizes and appenders. Async Logback appenders and external log agents usually provide better tail latency than synchronous remote logging. Keep payload logging narrow in production and rely on request IDs plus structured business events for most diagnostics.
