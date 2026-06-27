# Sprint 1 Summary — v0.1.0-alpha / v0.1.1-alpha

**Status:** ✅ Done

---

## v0.1.0-alpha — MVP

**Theme:** WebClient + Controller observability MVP

**Delivered:**
- o11y-kit-api: `HttpMetricRecorder` interface, `TraceIdResolver`
- o11y-kit-micrometer: `MicrometerHttpMetricRecorder`
- o11y-kit-spring-webmvc: `ServerObservationHandler` (HandlerInterceptor)
- o11y-kit-spring-webflux: `ClientObservationHandler` (ExchangeFilterFunction)
- o11y-kit-spring-boot-autoconfigure: zero-config auto-wiring
- o11y-kit-spring-boot-starter: aggregator POM
- order-demo migration: adapter layer ported to o11y-kit dependency

**Metrics:** o11y-kit 18 tests, order-demo 84 tests

---

## v0.1.1-alpha — Bugfix Release

**Theme:** Code review bugfixes (13 fixes from post-Sprint-1 max-effort review)

**Delivered:**
- Fixed P0: WebFlux auto-configuration missing (starter broke reactive apps)
- Fixed P0: Duplicate trace-id handling (TraceFilter removed, o11y-kit is single SOT)
- Fixed P1: MDC.remove not protected by try/finally
- Fixed P1: High-cardinality URI tag (fallback to "UNKNOWN")
- Fixed P1: `host:-1` port in metric tags
- Fixed P1: Malformed traceparent handling
- Fixed P2: HTTP 4xx/5xx double-counting
- Fixed P2: OTel Scope lifecycle in async pipeline
- Various P3 cleanups (dead code, unused imports)

**Metrics:** o11y-kit 24 tests, order-demo 96 tests