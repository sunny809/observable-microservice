# Migration Guide

## Upgrading from v0.1.0-alpha to v0.1.1-alpha

### TraceFilter removal

The `TraceFilter` Servlet filter has been removed. Its responsibilities (trace ID resolution, `X-Trace-Id` response header injection, MDC population) are now handled by `ServerObservationHandler`, which is auto-configured via `ObservationWebMvcAutoConfiguration`.

**Impact:** If you manually registered `TraceFilter` in your configuration, remove that bean definition. The auto-configuration handles everything.

```java
// BEFORE (v0.1.0-alpha) — remove this
@Bean
public FilterRegistrationBean<TraceFilter> traceFilter() {
    FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new TraceFilter());
    registration.addUrlPatterns("/*");
    return registration;
}
```

### Strict W3C traceparent validation

The `TraceIdResolver` now strictly validates the `traceparent` header per the W3C specification. Previously, loosely-formatted traceparent values may have been accepted. Now, only the format `00-<32hex>-<16hex>-<flags>` is accepted; invalid values fall back to a random UUID.

**Impact:** If downstream services were sending malformed `traceparent` headers, the trace ID will now differ (UUID fallback instead of a garbled value). Fix the downstream service to send a compliant header.

### Default exclusion patterns for actuator endpoints

`ObservationWebMvcAutoConfiguration` now excludes `/actuator/**` and `/health/**` from HTTP metrics by default. This prevents self-observation noise and Prometheus scrape feedback loops.

**Impact:** Actuator endpoints no longer generate `o11y.server.requests` timer data. If you relied on actuator metrics from o11y-kit, you can re-enable observation by registering your own `WebMvcConfigurer` that includes actuator paths:

```java
@Bean
public WebMvcConfigurer actuatorObservationConfigurer(ServerObservationHandler handler) {
    return new WebMvcConfigurer() {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(handler)
                    .addPathPatterns("/actuator/**", "/health/**");
        }
    };
}
```

---

## Upgrading from v0.1.1-alpha to v0.2.0-alpha

### BOM version bump

Update the o11y-kit BOM version in your `pom.xml`:

```xml
<!-- BEFORE -->
<o11y-kit.version>0.1.1-alpha</o11y-kit.version>

<!-- AFTER -->
<o11y-kit.version>0.2.0-alpha</o11y-kit.version>
```

### New client-side observability property

A new configuration property `o11y.kit.client.enabled` controls whether client-side HTTP observability (RestTemplate / RestClient interceptors) is active. It defaults to `true`, so no action is needed unless you want to disable it.

```yaml
# application.yml — no change required (default is true)
o11y:
  kit:
    client:
      enabled: true   # default; omit if you want client observation
```

To disable client observation:

```yaml
o11y:
  kit:
    client:
      enabled: false
```

### RestTemplate / RestClient auto-configuration

Client-side HTTP observability is now auto-configured for both `RestTemplate` and `RestClient`. If you have an existing `RestTemplate` bean, the interceptor is applied automatically via the auto-configuration.

**Before (v0.1.1-alpha):** No client-side metrics for RestTemplate or RestClient.

**After (v0.2.0-alpha):** Outbound calls via `RestTemplate` and `RestClient` automatically record `o11y.client.requests` timers and `o11y.client.errors` counters.

```xml
<!-- pom.xml — add the starter if not already present -->
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-spring-boot-starter</artifactId>
</dependency>
```

```yaml
# application.yml — default configuration (no changes required)
o11y:
  kit:
    client:
      enabled: true
      metrics:
        enabled: true
```

### New test utilities module

A new `o11y-kit-test` module provides `OtelTestHarness`, `MetricsAssertions`, and `RecordedSpan` for writing integration tests against the observability pipeline.

```xml
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-test</artifactId>
    <scope>test</scope>
</dependency>
```

```java
// Example usage
try (OtelTestHarness harness = OtelTestHarness.create()) {
    // ... exercise the system under test ...
    harness.flush();
    var spans = harness.getSpanExporter().getFinishedSpanItems();
    // assert on spans
}
```

---

## Common migration pitfalls

### Pitfall 1: Standalone MockMvc tests lose trace-id headers

`MockMvcBuilders.standaloneSetup()` does not fire `HandlerInterceptor`s — including `ServerObservationHandler`. If you previously asserted `X-Trace-Id` response headers in a standalone MockMvc test, those assertions will fail after upgrading to o11y-kit.

**Fix:** Use `MockMvcBuilders.webAppContextSetup()` with a full `@SpringBootTest` context, or use `@WebMvcTest` which loads the interceptor chain.

### Pitfall 2: WebClient filter already registered

If you manually add `ClientObservationHandler` as a WebClient filter AND the auto-configuration also adds it, you get double-registration. The interceptor has no dedup logic (unlike the RestTemplate customizer).

**Fix:** Remove the manual `.filter(obs)` call and let the auto-configuration handle it:

```java
// BEFORE
@Bean
WebClient myClient(WebClient.Builder builder, ClientObservationHandler obs) {
    return builder.filter(obs).build();
}

// AFTER — remove the manual filter, auto-config handles it
@Bean
WebClient myClient(WebClient.Builder builder) {
    return builder.baseUrl("http://svc").build();
}
```

### Pitfall 3: MockRestServiceServer and RestTemplateObservationInterceptor

When using `MockRestServiceServer.bindTo(restTemplate)`, the mock server wraps the interceptor chain. The `RestTemplateObservationInterceptor` still fires and records metrics to your `MeterRegistry`. If your test asserts that no metrics were recorded, it will fail because the interceptor records a timer for every matched mock response.

**Fix:** Either inject a mock `HttpMetricRecorder` (`verifyNoInteractions`), or assert that specific metrics were recorded with expected tags (recommended — this validates the observation path works).

### Pitfall 4: Missing Micrometer registry after upgrade

o11y-kit requires a `MeterRegistry` bean (typically `PrometheusMeterRegistry`). If your application didn't previously expose metrics, adding o11y-kit without a Micrometer registry will cause the `HttpMetricsAutoConfiguration` to silently back off — the app starts but no metrics are recorded.

**Fix:** Add a Micrometer registry dependency:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```
