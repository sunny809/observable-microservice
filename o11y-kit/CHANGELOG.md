# Changelog

All notable changes to o11y-kit are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.0-alpha] — 2026-08-01

Sprint 2: Unified HTTP client observability for all major Spring sync clients + static analysis toolchain.

### Added

- **o11y-kit-spring-webmvc**: `RestTemplateObservationInterceptor` — `ClientHttpRequestInterceptor` that records `o11y.client.requests` timers and optional OTel spans for every outbound RestTemplate call.
- **o11y-kit-spring-webmvc**: `RestClientObservationInterceptor` — Factory class providing `ClientHttpRequestInterceptor` instances for RestClient (Spring 6.2+), reusing the same implementation as RestTemplate.
- **o11y-kit-spring-webmvc**: `AbstractClientObservation` — Shared base class encapsulating metric recording, error classification, and OTel span lifecycle for sync HTTP clients.
- **o11y-kit-spring-webmvc**: `RestClientResponseAdapter` — Adapter bridging `RestClientResponseException` to `ClientHttpResponse` for error-path metric recording.
- **o11y-kit-spring-boot-autoconfigure**: `HttpClientObservationAutoConfiguration` — Auto-wires `RestTemplateCustomizer` and `RestClientCustomizer` beans that inject the observation interceptor into every Spring-managed RestTemplate / RestClient.Builder. Gated by `o11y.kit.client.enabled` (default `true`). Prevents double-registration.
- **o11y-kit-spring-boot-autoconfigure**: `O11yKitProperties` — `@ConfigurationProperties(prefix = "o11y.kit")` with nested `Client` (enabled, metrics.enabled) and `Server` (placeholder) namespaces.
- **o11y-kit-api**: `O11yKitOrders` — Constants for customizer ordering (`CLIENT_OBSERVATION = Integer.MAX_VALUE - 100`), zero-dependency.
- **o11y-kit-test**: New test utility module with `OtelTestHarness` (in-memory OTel SDK), `MetricsAssertions` (fluent Micrometer assertions), and `RecordedSpan` (span value type).
- **order-demo**: `RestTemplateConfig` + `WmsRestTemplateAdapter` — Demonstrates sync client observation with RestTemplate, producing the same metric series as the WebClient path.
- **CI**: `static-analysis` job in `.github/workflows/ci.yml` running SpotBugs, Checkstyle, JaCoCo on every PR.

### Changed

- `ObservationWebFluxAutoConfiguration` auto-config registration updated in `.imports` file (no behavioral change).

### Fixed

- SpotBugs `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` warnings on `O11yKitProperties` suppressed into baseline (acceptable for `@ConfigurationProperties` POJOs).

### Testing

- o11y-kit: **82 tests** (was 24 at Sprint 1 baseline, +58 across all modules)
  - api: 19 (+14 from TraceparentParameterizedTest)
  - micrometer: 5 (unchanged)
  - webmvc: 25 (+20 from RestTemplate, RestClient, OTel integration, ServerHandler expansion)
  - webflux: 7 (+3 from OTel integration)
  - autoconfigure: 16 (+5 from HttpClientAutoConfig, +1 from expanded O11yContextStartup)
  - test: 15 (new module)
- order-demo: **90 tests** (was 84 at Sprint 1.5, +6 from WmsRestTemplateAdapter + expanded tests)
- JaCoCo line coverage ≥ 80% enforced per o11y-kit module (excluding `*AutoConfiguration` boilerplate).

---

## [0.1.1-alpha] — 2026-07-21

Hotfix release addressing 13 issues found in post-Sprint-1 max-effort code review.

### Added

- **o11y-kit-spring-boot-autoconfigure**: `ObservationWebFluxAutoConfiguration` — provides `ClientObservationHandler` bean for reactive Spring Boot apps (fixes startup failure when starter is used in WebFlux context).
- **o11y-kit-spring-webmvc**: Default exclusion patterns `/actuator/**` and `/health/**` to prevent self-instrumentation and heartbeat metric pollution.
- **o11y-kit-spring-webflux**: Cancel-signal metric recording — `doFinally(SignalType.CANCEL)` now records `recordClientError("CANCELLED")` so cancelled outbound requests no longer disappear from telemetry.
- 6 new/expanded tests across server, client, and autoconfigure modules raising o11y-kit suite from 18 → 24 tests.

### Fixed

- **P0**: `ClientObservationHandler` was never wired as a Spring bean; reactive apps using the starter failed at startup. Added dedicated WebFlux auto-configuration.
- **P0**: Duplicate trace-id handling — `TraceFilter` (in order-demo) and `ServerObservationHandler` (in o11y-kit) both wrote to MDC and response headers. Removed the order-demo `TraceFilter`; o11y-kit is now the single source of truth.
- **P1**: `MDC.remove("traceId")` was not protected by try/finally — exceptions during metric recording leaked the MDC value into subsequent requests on the same thread.
- **P1**: `extractPattern()` fell back to the raw request URI when no Spring matching pattern existed, causing high-cardinality metric tags. Now returns the constant `"UNKNOWN"`.
- **P1**: `ClientObservationHandler.resolveHost()` produced `host:-1` for URLs without an explicit port. Now omits the port suffix when `URL.getPort() == -1`.
- **P1**: `TraceIdResolver` accepted malformed W3C `traceparent` values. Now strictly validates 4 dash-separated parts with a 32-hex trace-id segment; falls back to UUID otherwise.
- **P2**: HTTP 4xx/5xx responses were double-counted (once via `doOnNext`, again via `doOnError(WebClientResponseException)`). The error handler now skips metric recording for `WebClientResponseException` and only records non-HTTP errors (timeout, connection refused, DNS).
- **P2**: OTel `Scope` was opened with try-with-resources but the Reactor `Mono` subscribed asynchronously after the scope had already closed. Removed the broken try-with-resources block; documented full Reactor Context propagation as a Sprint 5 follow-up.
- **P3**: Removed dead `SERVER_ACTIVE` constant and stale Javadoc reference in `MicrometerHttpMetricRecorder`.
- **P3**: Removed unused `org.springframework.web.servlet.ModelAndView` import in `ServerObservationHandler`.
- **P3**: `o11y-kit-spring-boot-starter` POM did not declare a direct dependency on `o11y-kit-api`; relied on transitive resolution. Now declares the dependency explicitly.

### Removed

- **order-demo**: `TraceFilter.java` and `TraceFilterTest.java` — superseded by `ServerObservationHandler` from o11y-kit.

### Migration notes

- Apps that previously relied on `TraceFilter` for trace-id propagation: no action required — the same headers (`X-B3-TraceId`, `traceparent`) are still resolved, and the same response header (`X-Trace-Id`) is still set, by the o11y-kit interceptor.
- Apps using `MockMvcBuilders.standaloneSetup()` in tests will not exercise the interceptor (Spring limitation, unchanged from v0.1.0). Use `@SpringBootTest` + `MockMvc` to assert trace-id headers.

### Verified

```text
o11y-kit:   24 tests, 0 failures  ✅  (+6 vs v0.1.0)
order-demo: 96 tests, 0 failures  ✅  (TraceFilter tests removed)
```

---

## [0.1.0-alpha] — 2026-07-14

### Added

- **o11y-kit-api**: Core abstractions `HttpMetricRecorder` (interface) and `TraceIdResolver`.
- **o11y-kit-micrometer**: `MicrometerHttpMetricRecorder` — Micrometer-backed implementation of `HttpMetricRecorder`.
- **o11y-kit-spring-webmvc**: `ServerObservationHandler` — `HandlerInterceptor` for automatic Controller round-trip timing and HTTP status code distribution.
- **o11y-kit-spring-webflux**: `ClientObservationHandler` — `ExchangeFilterFunction` for automatic WebClient outbound metrics and optional OpenTelemetry tracing.
- **o11y-kit-spring-boot-autoconfigure**: `HttpMetricsAutoConfiguration` + `ObservationWebMvcAutoConfiguration` — zero-config auto-wiring.
- **o11y-kit-spring-boot-starter**: Aggregator POM — one dependency to import all modules.
- **order-demo migration**: Refactored order-adapter from inline o11y code to `o11y-kit-spring-boot-starter` dependency.

### Fixed

- `MicrometerHttpMetricRecorder` client timer incorrectly used `serverTimerCache` registry, causing tag collision when method+host+status matched method+uri+status from a different request type.
- `ClientObservationHandler` no longer depends on `TracerHelper` static; accepts `Tracer` via constructor (metrics-only mode when null).
- `ObservationConfig` circular dependency during bean creation — split into `HttpMetricsConfig` (bean declarations) and `ObservationConfig` (WebMvcConfigurer).
- `TraceIdResolver` removed custom `Function<T,R>` interface, now uses `java.util.function.Function`.

### Security

- n/a (alpha release, no production use yet)
