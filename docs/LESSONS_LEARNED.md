# Lessons Learned

This document captures the real-world challenges, design trade-offs, and iterative thinking that went into building this order service. It is intended to show the evolution of ideas and the reasoning behind key decisions.

## TransactionTemplate in Async WMS Callbacks

**The Problem:**
Initially, I tried using `@Async` for the WMS callback after the order transaction committed. The idea was simple: fire the WMS call in a background thread and update the order status when it returned. However, I spent an entire day debugging why database updates in the callback weren't persisting.

**What I Learned:**
Spring's `@Transactional` context does not propagate to Reactor Netty threads (used by WebClient). Even though the callback ran after the transaction committed, any database operations in `thenAccept` or `exceptionally` were silently ignored because there was no active transaction.

**The Solution:**
I switched to `TransactionTemplate.executeWithoutResult()` to explicitly create a new transaction for each database operation in the async callback. This is documented in ADR-1.

**Alternative Considered:**
I briefly considered using a message queue (Kafka) for the WMS callback instead of `CompletableFuture`, but that would have introduced significant infrastructure complexity for what is essentially a single downstream call. I kept the `CompletableFuture` approach but added a TODO for future Kafka migration.

## Circuit Breaker Threshold Tuning

**The Problem:**
I started with a Resilience4j circuit breaker failure rate threshold of 20%. During testing with simulated network failures, I found this was far too aggressive — brief blips (e.g., a single timeout) would open the circuit for 30 seconds, causing unnecessary 500 errors.

**What I Learned:**
Circuit breaker thresholds need to balance sensitivity with tolerance for transient failures. After testing with various scenarios, I settled on 50% failure rate with a 10-call sliding window. This means the circuit only opens after 5 consecutive failures, which is more appropriate for HTTP services with occasional timeouts.

**The Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        failureRateThreshold: 50
        slidingWindowSize: 10
        waitDurationInOpenState: 30s
```

## Idempotency Cache vs Database

**The Problem:**
Early versions of the idempotency check queried the database on every request. For high-traffic scenarios, this created unnecessary load on PostgreSQL.

**What I Learned:**
A two-tier approach works best: Caffeine in-memory cache for sub-millisecond hot-path detection, with a database fallback for correctness across service restarts. The cache has a 30-minute TTL, which is a trade-off — too short and you lose the performance benefit; too long and you risk memory pressure.

**The Trade-off:**
- Cache hit: ~0.1ms response time
- Cache miss + DB lookup: ~5ms response time
- Full DB write: ~15ms response time

This is acceptable for our use case, but in a multi-instance deployment, the cache inconsistency across instances would require Redis or Hazelcast.

## Span Lifecycle in WmsRestAdapter

**The Problem:**
OpenTelemetry spans are designed to measure the duration of an operation. In `WmsRestAdapter.sendInstruction()`, I create a span, make an async WebClient call, and return a `CompletableFuture`. The span ends in the `finally` block before the HTTP call completes.

**What I Learned:**
This means the span duration only covers the synchronous setup phase (~1ms), not the actual HTTP call duration (~50-200ms). HTTP errors are also not recorded on the span because it has already ended.

**Future Fix:**
I documented this as ADR-3 with a proposed fix: move `span.end()` into `CompletableFuture.whenComplete()`. However, this requires careful handling of the span context across threads, so I left it as a known issue.

## Jackson Mixins for Domain Serialization

**The Problem:**
I wanted to keep domain classes (`OrderItem`, `Order`) free of Jackson annotations to maintain framework independence. But the persistence adapter needs to serialize them to JSON for the database.

**What I Learned:**
Jackson Mixins allow you to add serialization rules without modifying the domain classes. This is a clean solution, but it has a subtle issue: serialization failures silently return empty arrays (logged as warnings). I spent an hour debugging why orders were saving with empty item lists before realizing the mixin wasn't handling null collections correctly.

**The Fix:**
Added null checks in the mixin and defensive copies in the `Order` constructor.

## Testing the Saga Pattern

**The Problem:**
Testing `OrderPlacementSaga` was the hardest part of this project. The saga involves multiple async operations, compensation logic, and event publishing. A simple unit test with mocked ports wasn't enough — I needed to verify that compensation runs in the right order.

**What I Learned:**
I used `CountDownLatch` and `Awaitility` to wait for async operations in tests. For BDD tests, I used WireMock to simulate inventory service responses and Testcontainers for PostgreSQL. The key insight was to test the saga as a black box: verify the final state (order status, inventory reservations) rather than the internal steps.

**Test Coverage:**
- Unit tests: 42 test methods for saga logic
- Integration tests: JPA repository tests with `@DataJpaTest`
- BDD tests: 13 Cucumber scenarios covering happy path, failures, and edge cases
- Architecture tests: ArchUnit rules enforcing hexagonal boundaries

## Hexagonal Architecture in Spring Boot

**The Problem:**
Spring Boot encourages a layered architecture (Controller → Service → Repository). Moving to hexagonal architecture (Ports & Adapters) required rethinking the module structure.

**What I Learned:**
The key is to invert dependencies: the domain layer (`order-application`) defines interfaces (ports), and the infrastructure layer (`order-adapter`) implements them. This means `order-application` has zero dependencies on Spring Framework — only `spring-context` and `spring-tx` for `@Service` and `@Transactional`.

**The Challenge:**
Spring Boot's auto-configuration expects beans to be in the same package or sub-packages. Splitting the project into modules required explicit `@ComponentScan` and `@Import` configurations in `order-infrastructure`.

**The Payoff:**
- Domain logic is fully testable without Spring context
- Framework changes (e.g., switching from JPA to MongoDB) don't affect the domain layer
- ArchUnit tests enforce these boundaries at build time

## Docker Multi-Stage Build

**The Problem:**
The initial Dockerfile used a single stage with Maven and the full JDK, resulting in a 500MB+ image.

**What I Learned:**
Multi-stage builds are essential for production images:
1. Stage 1: Build with Maven + JDK
2. Stage 2: Runtime with JRE only

I also added security hardening:
- Non-root user (`appuser`, UID 1000)
- Read-only root filesystem
- Distroless base image (Alpine JRE)

**The Result:**
Image size dropped from 500MB to ~180MB. Trivy vulnerability scanning shows zero critical/high vulnerabilities.

## What I Would Do Differently

1. **Use Kafka for WMS callbacks from the start**: The current Spring Events approach works for a single instance but doesn't scale. I would implement the Outbox pattern with Kafka for reliable event publishing.

2. **Add saga timeout handling**: Currently, if the WMS callback never arrives, the order stays in `CREATED` status indefinitely. I would add a scheduled job to mark stale orders as `REJECTED` after a timeout.

3. **Use Redis for distributed caching**: Caffeine is great for single-instance but requires Redis or Hazelcast in a multi-instance deployment.

4. **Add a dead letter queue for failed compensations**: If inventory release fails during compensation, the saga logs the error but doesn't retry. A DLQ would ensure eventual consistency.

5. **Implement API versioning from the start**: I added `/api/v1/orders` late in the project. Starting with versioning would have made the API evolution cleaner.
