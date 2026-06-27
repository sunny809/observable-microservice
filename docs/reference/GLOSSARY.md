# Glossary

## Domain Terms

| Term | Definition |
|------|-----------|
| **Order** | Aggregate root representing a customer's purchase order. Contains customer ID, items, status, idempotency key |
| **Saga** | Distributed transaction pattern that coordinates multiple service calls with compensating transactions on failure |
| **Compensation** | A rollback action that undoes a previous saga step (e.g., releasing inventory after a WMS failure) |
| **Inventory Reservation** | Temporary hold of stock for an order item. States: PENDING → CONFIRMED or RELEASED |
| **WMS** | Warehouse Management System — external service that handles physical picking of items |
| **TMS** | Transportation Management System — external service that handles shipment dispatch |
| **Idempotency** | Guarantee that processing the same request multiple times produces the same result. Implemented via Caffeine cache (fast) + DB constraint (durable) |
| **Order Status** | State machine: CREATED → WMS_ACKED → WMS_PICKED → TMS_DISPATCHED. Failure branches: REJECTED, TMS_REJECTED |

## Observability Terms

| Term | Definition |
|------|-----------|
| **o11y** | Numeronym for **obser**vability (11 letters between o and y). Used as prefix for metrics and module names |
| **Micrometer** | Metrics instrumentation library for JVM apps. Provides dimensional metrics with tag support |
| **MeterRegistry** | Micrometer's central registry for creating meters (Timers, Counters, etc.) |
| **Timer** | Micrometer meter measuring duration and recording count/total/max/pXX percentile |
| **Counter** | Micrometer meter measuring a monotonically increasing value |
| **OpenTelemetry (OTel)** | Vendor-agnostic observability framework for traces, metrics, and logs |
| **Span** | A named, timed operation in OTel. Child spans form a distributed trace |
| **Trace** | A tree of spans representing a complete request flow across service boundaries |
| **Trace ID** | 32-character hex string uniquely identifying a trace. Propagated via `traceparent` (W3C) or `X-B3-TraceId` (Zipkin) headers |
| **Jaeger** | Open-source distributed tracing UI and backend (receives OTLP via gRPC on port 4317) |
| **OTLP** | OpenTelemetry Protocol — the gRPC-based transport for sending trace/metric data to backends |
| **ECS** | Elastic Common Schema — a standardized field naming convention for log events in the ELK stack |
| **ELK** | Elasticsearch + Logstash + Kibana — log aggregation and analysis pipeline |
| **LogstashEncoder** | Logback encoder from `net.logstash.logback` that produces JSON-formatted log output |
| **MDC** | Mapped Diagnostic Context — thread-local map for contextual logging (traceId, service name, etc.) |
| **Prometheus** | Metrics monitoring system that scrapes HTTP endpoints (e.g., `/actuator/prometheus`) |

## Architecture Terms

| Term | Definition |
|------|-----------|
| **Hexagonal Architecture** | Port/Adapter pattern: core domain logic (ports) independent of frameworks; infrastructure (adapters) implements ports |
| **Port** | Interface in the domain layer defining a boundary (inbound: use cases; outbound: repositories, external services) |
| **Adapter** | Implementation of a port in the infrastructure layer (REST controllers, JPA repos, HTTP clients) |
| **ArchUnit** | Test library that enforces architecture rules at compile time (e.g., "domain must not depend on web") |
| **ADR** | Architecture Decision Record — documents a design decision with context, options, trade-offs |
| **Resilience4j** | Fault-tolerance library providing Circuit Breaker, Retry, Rate Limiter, Time Limiter |
| **Circuit Breaker** | Pattern that stops calls to a failing service, allowing it to recover. Config: 50% failure threshold, 5 minimum calls |

## Technology Terms

| Term | Definition |
|------|-----------|
| **RestTemplate** | Spring's synchronous HTTP client (blocking). Instrumented by o11y-kit since v0.2.0 |
| **RestClient** | Spring 6.2+ fluent HTTP client (blocking). Instrumented by o11y-kit since v0.2.0 |
| **WebClient** | Spring's reactive HTTP client (non-blocking). Instrumented by o11y-kit since v0.1.0 |
| **CompletableFuture** | Java async primitive used to compose saga steps with `thenCompose` / `thenRun` / `exceptionally` |
| **TransactionTemplate** | Spring utility for explicit programmatic transaction management (used in async callbacks where `@Transactional` does not propagate) |
| **@TransactionalEventListener** | Spring event listener that fires after the publishing transaction commits (phase = AFTER_COMMIT) |
| **Flyway** | Database migration tool. SQL scripts in `order-infrastructure/src/main/resources/db/migration/` |
| **Caffeine** | High-performance in-memory cache. Used for idempotency key fast-path (30s TTL, 10,000 entries) |
| **Testcontainers** | Java library providing disposable Docker containers for integration tests |
| **WireMock** | HTTP mock server for simulating external services in BDD tests |
| **Cucumber** | BDD test framework using Gherkin feature files (Given/When/Then) |
| **SpotBugs** | Static analysis tool for finding Java bugs. Runs in the `static-analysis` Maven profile |
| **Checkstyle** | Static analysis tool enforcing coding standards. Uses Google Java Style. Runs in `static-analysis` profile |

## Sprint Process Terms

| Term | Definition |
|------|-----------|
| **Sprint** | Time-boxed development iteration producing a potentially releasable increment (v0.x.x-beta) |
| **DoD** | Definition of Done — checklist that must be satisfied for a story/sprint to be considered complete |
| **RACI** | Responsible/Accountable/Consulted/Informed — role assignment matrix |
| **US** | User Story — a feature description from the user's perspective |
| **AC** | Acceptance Criteria — specific conditions that must be met for a story to be accepted |