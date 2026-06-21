# Lessons Learned

This document captures real-world engineering challenges, design trade-offs, and the reasoning behind key decisions. It is intended to help readers understand not just what was built, but why it was built that way.

---

## TransactionTemplate in Async Callbacks

**The Problem:**
Async callbacks from `CompletableFuture` chains run on Reactor Netty threads. Spring's `@Transactional` context does not propagate to these threads — database operations in `thenAccept` or `exceptionally` are silently ignored.

**The Solution:**
Wrap all database operations in async callbacks with `TransactionTemplate.executeWithoutResult()` to explicitly create a new transaction. Documented in ADR-1.

**Key Insight:**
When mixing reactive HTTP clients (WebClient) with imperative transaction management, assume no transaction context propagation. Always wrap data access in explicit transaction boundaries.

---

## Circuit Breaker Threshold Tuning

The initial circuit breaker configuration used a 10% failure rate threshold. This was too sensitive for a service with low traffic — a single timeout in a 10-request window would open the circuit.

**Final configuration:**

- Failure rate threshold: **50%**
- Minimum number of calls: **5** (prevents false positives on low traffic)
- Sliding window: **10 calls**, count-based
- Wait duration in open state: **30 seconds**

**Key Insight:**
Circuit breaker thresholds must account for traffic volume. A 50% threshold with a minimum call count of 5 means at least 3 out of 5 calls must fail before the circuit opens. This prevents single transient failures from triggering cascading outages.

---

## Dual-Layer Idempotency Trade-offs

**The approach:**

1. Caffeine cache (fast path): 30-minute TTL, max 10,000 entries
2. Database unique constraint (source of truth)

**Why not just the database?**
A database query for idempotency check takes 2-5ms. The Caffeine cache provides sub-millisecond lookup for hot keys. In a 1000 RPM scenario, the cache handles ~99% of duplicate checks without touching the database.

**Why not just the cache?**
The cache has a 30-minute TTL and is in-memory. A service restart clears it. The database constraint ensures correctness across restarts and prevents the race condition where two concurrent requests with the same idempotency key both miss the cache.

**Key Insight:**
Caching for idempotency is a performance optimization, not a correctness mechanism. Always pair it with a durable source of truth.

---

## Async Saga Orchestration Complexity

The saga uses `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` to fire async operations after the HTTP response is sent. This gives the user a fast 201 response while the heavy lifting (WMS/TMS communication) happens in the background.

**Challenges encountered:**

1. Transaction context loss on Reactor threads (see above)
2. Event ordering — WMS confirmation must complete before TMS dispatch starts
3. Error handling in async chains — exceptions in `thenCompose` are silently swallowed unless caught in `exceptionally`
4. Compensating transactions must be idempotent (releasing an already-released reservation should be safe)

**Pattern that emerged:**

```java
@TransactionalEventListener(AFTER_COMMIT)
void onEvent(Event event) {
    service.call()
        .thenCompose(result -> {
            if (result.isSuccess()) {
                return nextStep()
                    .thenRun(() -> transactionTemplate.executeWithoutResult(tx -> {
                        // update status, log saga step
                    }));
            } else {
                return compensate()
                    .thenRun(() -> transactionTemplate.executeWithoutResult(tx -> {
                        // update status to REJECTED, log compensation
                    }));
            }
        })
        .exceptionally(ex -> {
            transactionTemplate.executeWithoutResult(tx -> {
                compensate();
                updateStatus(REJECTED);
            });
            return null;
        });
}
```

---

## WebClient + Resilience4j: Non-Blocking Fallback

Resilience4j's `@CircuitBreaker` with `fallbackMethod` requires the fallback method to have the same return type as the original method. When migrating from blocking (`restTemplate`) to non-blocking (`webClient`), the fallback method must return a `CompletableFuture`, not throw an exception directly.

**Before (blocking):**

```java
public InventoryReservation handleOccupyFallback(...) {
    throw new RuntimeException("Inventory service unavailable");
}
```

**After (non-blocking):**

```java
public CompletableFuture<InventoryReservation> handleOccupyFallback(...) {
    return CompletableFuture.failedFuture(new RuntimeException("Inventory service unavailable"));
}
```

---

## Observability: SDK Extraction as a Project Matures

The observability interceptors started as inline code in the adapter module. As they were refined (fixing double-counting of HTTP errors, resolving trace ID format issues, adding metrics for cancelled requests), it became clear that the code had become a reusable library.

**Signals that it was time to extract:**

1. The interceptor code had no business logic — it was pure cross-cutting concern
2. Multiple adapters needed the same pattern (WebClient, RestTemplate, RestClient)
3. Tests for observability outnumbered tests for some business features
4. Configuration properties (`o11y.kit.*`) had their own lifecycle independent of the application

The extraction produced [o11y-kit](o11y-kit/), a separate 7-module SDK with its own versioning, test suite (82 tests), and documentation.

---

## Security: Layered Approach for Different Environments

The blueprint supports three security profiles:

| Profile | Security | Use Case |
|---------|----------|----------|
| `default` | JWT/OAuth2 required | Production-like |
| `local` | All endpoints permitted | Local development |
| `test` | All endpoints permitted | Automated tests |

The `test` profile uses a dedicated `TestSecurityConfig` to avoid loading the `SecurityConfig` during integration tests. This prevents JWT validation from blocking test HTTP calls.

**Key Insight:**
Security configuration is an environment concern, not a code concern. Use Spring profiles to switch between permissive and strict configurations without changing application code.
