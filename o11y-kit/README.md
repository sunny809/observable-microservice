# o11y-kit

[![Build Status](https://img.shields.io/github/actions/workflow/status/example/order-demo/ci.yml?branch=main)](https://github.com/example/order-demo/actions)
[![Version](https://img.shields.io/badge/version-0.2.0--alpha-blue)](CHANGELOG.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0+-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org)
[![Coverage](https://img.shields.io/badge/coverage-%3E%3D80%25-brightgreen)](../.github/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow)](../LICENSE)

A **drop-in HTTP observability toolkit** for Spring Boot 3.x. Add one dependency, restart your app, and every HTTP request — inbound (Controller) and outbound (WebClient / RestTemplate / RestClient) — automatically produces Micrometer metrics and optional OpenTelemetry spans. **Zero code changes.**

```text
Add dependency → restart → /actuator/prometheus shows o11y_server_requests + o11y_client_requests
```

---

## Why o11y-kit?

Spring Boot already bundles Micrometer, so why a separate library?

| Problem | o11y-kit solution |
|---------|-------------------|
| Micrometer's `@Observed` / `ObservationRegistry` requires manual annotation on every controller and client | **Auto-interception**: `HandlerInterceptor` + `ExchangeFilterFunction` + `RestTemplateCustomizer` captures all HTTP traffic without touching business code |
| Spring Cloud Sleuth is deprecated; Micrometer Tracing has a different API than Micrometer Metrics | **Unified abstraction**: one `HttpMetricRecorder` interface drives both metrics and tracing |
| WebClient, RestTemplate, and RestClient each need different instrumentation | **One abstraction, all clients**: the same `o11y.client.requests` timer series is produced regardless of which HTTP client your app uses |
| Adding an interceptor/filter to every client bean is tedious and error-prone | **Zero-config wiring**: Spring Boot `RestTemplateCustomizer` / `RestClientCustomizer` inject the interceptor into every managed bean automatically |

**Core principles:** non-invasive, zero-code, lightweight (no agent, no bytecode weaving), Micrometer + OTel optional, framework-agnostic core.

---

## Quickstart

### 1. Add the starter

```xml
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-spring-boot-starter</artifactId>
    <version>0.2.0-alpha</version>
</dependency>
```

Add a Prometheus registry (or any Micrometer-compatible registry):

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 2. Restart and verify

```bash
curl http://localhost:8080/actuator/prometheus | grep o11y
```

You should see:

```prometheus
# HELP o11y_server_requests_seconds Server HTTP request duration
o11y_server_requests_seconds_count{method="POST",uri="/api/orders",status="201"} 1.0

# HELP o11y_client_requests_seconds Client HTTP request duration
o11y_client_requests_seconds_count{method="POST",host="inventory-svc:8080",status="200"} 1.0
```

**No further configuration required.** Controller endpoints are intercepted automatically. Each HTTP response sets an `X-Trace-Id` header for end-to-end correlation.

### 3. Outbound calls — pick any client

All three produce the **same** `o11y.client.requests` metric series.

**WebClient** (reactive) — add the filter:

```java
@Bean
WebClient inventoryWebClient(WebClient.Builder builder,
                              ClientObservationHandler obs) {
    return builder.baseUrl("http://inventory-svc")
                  .filter(obs)
                  .build();
}
```

**RestTemplate** (blocking) — zero code, auto-wired:

```java
@Bean
RestTemplate inventoryRestTemplate(RestTemplateBuilder builder) {
    return builder.rootUri("http://inventory-svc").build();
    // o11y-kit RestTemplateCustomizer adds the interceptor automatically
}
```

**RestClient** (Spring 6.2+ fluent) — zero code, auto-wired:

```java
@Bean
RestClient inventoryRestClient(RestClient.Builder builder) {
    return builder.baseUrl("http://inventory-svc").build();
    // o11y-kit RestClientCustomizer adds the interceptor automatically
}
```

---

## Modules

| Module | Artifact | Purpose | Since |
|--------|----------|---------|-------|
| **api** | `o11y-kit-api` | Zero-dependency core abstractions: `HttpMetricRecorder`, `TraceIdResolver`, `O11yKitOrders` | 0.1.0 |
| **micrometer** | `o11y-kit-micrometer` | `MicrometerHttpMetricRecorder` — Micrometer-backed implementation | 0.1.0 |
| **spring-webmvc** | `o11y-kit-spring-webmvc` | Inbound: `ServerObservationHandler`. Outbound: `RestTemplateObservationInterceptor`, `RestClientObservationInterceptor`, `AbstractClientObservation` | 0.1.0 |
| **spring-webflux** | `o11y-kit-spring-webflux` | `ClientObservationHandler` — WebClient `ExchangeFilterFunction` | 0.1.0 |
| **spring-boot-autoconfigure** | `o11y-kit-spring-boot-autoconfigure` | Auto-configurations + `O11yKitProperties` binding | 0.1.0 |
| **spring-boot-starter** | `o11y-kit-spring-boot-starter` | Aggregator — one dependency to import all modules | 0.1.0 |
| **test** | `o11y-kit-test` | `OtelTestHarness`, `MetricsAssertions`, `RecordedSpan` for integration tests | 0.2.0 |

### Module dependency graph

```text
                       +-------------------+
                       |  o11y-kit-api     |  (zero-framework)
                       +---------+---------+
                                 |
        +------------------------+------------------------+
        |                        |                        |
        v                        v                        v
+---------------+      +-------------------+    +----------------------+
| o11y-kit-     |      | o11y-kit-spring-  |    | o11y-kit-spring-     |
| micrometer    |      | webmvc            |    | webflux              |
+-------+-------+      +---------+---------+    +-----------+----------+
        |                        |                          |
        +------------+-----------+--------------------------+
                     |
                     v
        +------------------------------+
        | o11y-kit-spring-boot-        |
        | autoconfigure                |
        +--------------+---------------+
                       |
                       v
        +------------------------------+
        | o11y-kit-spring-boot-starter |
        +------------------------------+
        | o11y-kit-test (test scope)   |
        +------------------------------+
```

`o11y-kit-api` has **no Spring or Micrometer dependencies** — applications that cannot use Spring can implement `HttpMetricRecorder` directly.

---

## Configuration

All properties bind under `o11y.kit`.

| Property | Type | Default | Since | Description |
|----------|------|---------|-------|-------------|
| `o11y.kit.client.enabled` | boolean | `true` | 0.2.0 | Master switch for outbound HTTP observability. When `false`, client customizers are not registered. |
| `o11y.kit.client.metrics.enabled` | boolean | `true` | 0.2.0 | Controls emission of client-side Micrometer timers and counters. |
| `o11y.kit.server.*` | *reserved* | — | 0.2.0 | Namespace for inbound observability properties (enable/disable, exclusion patterns). Not yet bound. |

Example:

```yaml
o11y:
  kit:
    client:
      enabled: true
      metrics:
        enabled: true
```

---

## Metrics reference

| Metric name | Type | Tags | Description |
|-------------|------|------|-------------|
| `o11y.server.requests` | Timer | `method`, `uri`, `status` | Inbound Controller request duration |
| `o11y.client.requests` | Timer | `method`, `host`, `status` | Outbound HTTP call round-trip duration |
| `o11y.client.errors` | Counter | `method`, `host`, `error` | Outbound call errors (timeout, connection refused, DNS, cancelled) |

Status codes are bucketed into `2xx`, `4xx`, `5xx` groups to prevent high-cardinality tag explosion.

---

## FAQ

### How is this different from Micrometer's built-in observation?

Micrometer's `@Observed` requires annotating every method you want to observe. o11y-kit uses Spring's interceptor/filter SPIs to capture **all** HTTP traffic automatically — no annotations, no code changes. The trade-off: o11y-kit is coarser-grained (per-endpoint, not per-method).

### Do I need OpenTelemetry?

No. OTel is optional. Without it, all Micrometer metrics are still recorded. When `opentelemetry-api` is on the classpath, the interceptors automatically create child spans for each outbound call — no configuration needed.

### Can I use this with RestTemplate? RestClient?

Yes since v0.2.0-alpha. Declare a `RestTemplate` bean via `RestTemplateBuilder` and o11y-kit's `RestTemplateCustomizer` adds the interceptor automatically. Same for `RestClient.Builder`.

### What about Feign?

Spring Cloud OpenFeign entered maintenance mode in 2022. This project focuses on Spring's native HTTP clients (WebClient, RestTemplate, RestClient) and the upcoming declarative HTTP interfaces (`HttpServiceProxyFactory`). Feign support may be considered in a future major release based on community demand.

### How do I exclude endpoints from metrics?

Inbound: `/actuator/**` and `/health/**` are excluded by default. Add custom exclusions by registering a `WebMvcConfigurer`:

```java
@Bean
WebMvcConfigurer customExclusions(ServerObservationHandler handler) {
    return new WebMvcConfigurer() {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(handler)
                    .addPathPatterns("/**")
                    .excludePathPatterns("/actuator/**", "/health/**", "/custom/**");
        }
    };
}
```

### Can I provide my own HttpMetricRecorder?

Yes. Define a `@Bean` of type `HttpMetricRecorder` and the auto-configuration uses it instead of the default Micrometer-backed one.

```java
@Bean
HttpMetricRecorder myRecorder() {
    return new MyCustomRecorder();
}
```

### Is this production ready?

v0.2.0-alpha — public API is stabilizing before v1.0. Used in the [order-demo](https://github.com/example/order-demo) reference application (172 tests). Static analysis (SpotBugs + Checkstyle + JaCoCo ≥80%) runs on every commit. Test count: 82 (o11y-kit) with zero regressions across versions.

### How do I write integration tests for o11y-kit?

Use `o11y-kit-test`:

```java
@Test
void testWebClientRecordsMetrics() {
    try (OtelTestHarness harness = OtelTestHarness.create()) {
        // exercise WebClient...
        harness.flush();
        assertThat(harness.getSpans()).hasSize(1);
    }
}
```

---

## Compatibility

| Component | Version |
|-----------|---------|
| Spring Boot | 3.0+ (tested 3.4.x) |
| Java | 17+ (built with Java 21) |
| Micrometer | 1.12+ (transitive via Spring Boot) |
| OpenTelemetry | 1.30+ (optional) |
| WebClient (reactive) | Spring WebFlux 6.x |
| RestTemplate (blocking) | Spring Web 6.x |
| RestClient (fluent) | Spring Web 6.x (Boot 3.2+) |

No Spring Cloud, Sleuth, or actuator required (actuator recommended for Prometheus endpoint).

---

## Roadmap

| Version | Focus | Status |
|---------|-------|--------|
| v0.1.0-alpha | WebClient + Controller MVP | ✅ Done |
| v0.1.1-alpha | Code review bugfixes (13 fixes) | ✅ Done |
| **v0.2.0-alpha** | **RestTemplate + RestClient + Static Analysis** | **← Current** |
| v0.3.0-beta | `@Observed` annotation + Feign + NullAway | Planned |
| v0.4.0-beta | Production hardening + mutation testing + benchmarks | Planned |
| v0.5.0-rc.1 | MDC chain + adapter SPI | Planned |
| v1.0.0-GA | Maven Central + SonarCloud + docs site | Q4 2026 |

[CHANGELOG.md](CHANGELOG.md) for full release notes.

---

## Reference implementation

The [order-demo](https://github.com/example/order-demo) project is the canonical reference: a Saga-pattern distributed order service using `o11y-kit-spring-boot-starter` as its observability layer with both WebClient and RestTemplate adapters.

---

## License

Apache License, Version 2.0. See [LICENSE](../LICENSE).
