# o11y-kit

[![Version](https://img.shields.io/badge/version-0.5.0-blue)](CHANGELOG.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4+-brightgreen)](https://spring.io/projects/spring-boot)

Lightweight, non-invasive HTTP observability SDK for Spring Boot.

## Modules

| Module | Description |
|--------|-------------|
| `o11y-kit-core` | Core library: HTTP metric recording, WebMVC/WebFlux integration, AOP support |
| `o11y-kit-spring-boot-starter` | Spring Boot auto-configuration — add this dependency to get started |
| `o11y-kit-test` | Test harness and assertion utilities for integration tests |

## Quick Start

Add the starter dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-spring-boot-starter</artifactId>
    <version>0.5.0</version>
</dependency>
```

That's it. HTTP server requests, client calls, and metrics are automatically recorded.

## Features

- **HTTP Server Metrics** — automatically record request duration, status code, and method for every controller (WebMVC + WebFlux)
- **HTTP Client Metrics** — record outbound HTTP call metrics for RestTemplate, RestClient, and WebClient
- **Direct Micrometer API** — use `MeterRegistry` directly for business metrics, no extra abstraction layer
- **@Observed Annotation** — AOP-based method-level observation with success/failure outcomes
- **Trace ID Propagation** — automatic trace ID resolution from HTTP headers (W3C traceparent)

## Recording Business Metrics

o11y-kit integrates with Spring Boot's Micrometer auto-configuration. Inject `MeterRegistry` directly to record your own metrics:

```java
@Component
public class OrderMetrics {
    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordOrderPlaced(String status) {
        registry.counter("orders.placed", "status", status).increment();
    }

    public void recordSagaDuration(long ms, String outcome) {
        Timer.builder("saga.duration")
                .description("End-to-end duration of a single order placement saga")
                .tag("outcome", outcome)
                .register(registry)
                .record(ms, TimeUnit.MILLISECONDS);
    }
}
```

## Building

```bash
# Build core module
mvn clean install -pl o11y-kit-core -am

# Run tests
mvn test -pl o11y-kit-core
```

## License

Apache License 2.0