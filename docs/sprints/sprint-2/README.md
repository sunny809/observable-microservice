# Sprint 2 Summary — v0.2.0-alpha

**Status:** ✅ Done (current release)

---

**Theme:** Unified HTTP client observability for all major Spring sync clients + static analysis toolchain

**Delivered:**

### o11y-kit
- `RestTemplateObservationInterceptor` — `ClientHttpRequestInterceptor` for RestTemplate
- `RestClientObservationInterceptor` — factory class for RestClient
- `AbstractClientObservation` — shared base for sync client metric recording + OTel span lifecycle
- `RestClientResponseAdapter` — bridges `RestClientResponseException` to `ClientHttpResponse`
- `HttpClientObservationAutoConfiguration` — auto-wires `RestTemplateCustomizer` + `RestClientCustomizer`
- `O11yKitProperties` — `@ConfigurationProperties(prefix = "o11y.kit")` with Client/Server namespaces
- `O11yKitOrders` — constants for customizer ordering
- `o11y-kit-test` — new test utility module (OtelTestHarness, MetricsAssertions, RecordedSpan)
- CI: `static-analysis` job running SpotBugs + Checkstyle + JaCoCo

### order-demo
- `RestTemplateConfig` + `WmsRestTemplateAdapter` — demonstrates sync client observation
- TMS saga completion (full dispatch flow)
- Expanded BDD scenarios (TMS, validation)

### Static Analysis
- SpotBugs with FindSecBugs (`static-analysis` Maven profile)
- Checkstyle (Google Java Style)
- JaCoCo 80% line coverage threshold per o11y-kit module
- Error Prone (separate profile)

**Metrics:** o11y-kit 82 tests, order-demo 90 tests

**Key ADRs:** ADR-5 (o11y-kit extraction), ADR-6 (Jacoco 80% threshold)