# Modernization Plan — 2025/2026 Java + Spring Best Practices

> This document records modernization opportunities identified during Sprint 4 review,
> based on JDK 21 LTS features (Virtual Threads, Structured Concurrency, Records, ScopedValue)
> and Spring Boot 3.x ecosystem evolution.
>
> Items are prioritized by risk/benefit and slotted into future sprints.
> They are NOT all required — pick what fits the project's direction.

---

## Priority Overview

| Priority | Theme | Sprint | Items |
|----------|-------|--------|-------|
| 🔴 P0 | Modern Java basics | Sprint 5 | Records, ProblemDetail, Structured Concurrency, Future observability |
| 🔴 P1 | Virtual Threads | Sprint 6 | Enable VT, unify HTTP clients, collapse Reactor work |
| 🟡 P2 | Observation framework | Sprint 6 | Replace manual OTel spans with Observation |
| 🟢 P3 | Future exploration | Backlog | ScopedValue, declarative HTTP interfaces |

---

## Item Catalog

### 🔴 P0 — Sprint 5: Modern Java Basics

#### US-5.1: POJO → Record (6 files)

| File | Fields | Impact |
|------|--------|--------|
| `WmsShipmentInstruction.java` | 2 | Low |
| `TmsShipmentInstruction.java` | 2 | Low |
| `OrderItem.java` | 2 | Low |
| `PlaceOrderCommand.java` | 3 | Low |
| `OrderResponse.java` | 3 | Low |
| `WmsInstructionRequiredEvent.java` | 3 | Low |

**Migration pattern:**

```java
// Before
public class OrderItem {
    private final String sku;
    private final int quantity;
    public OrderItem(String sku, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException();
        this.sku = sku; this.quantity = quantity;
    }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
}

// After
public record OrderItem(String sku, int quantity) {
    public OrderItem {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
    }
}
```

**Risk:** None — pure syntactic change, same semantics.

---

#### US-5.2: StructuredTaskScope for parallel inventory reservations

**Current (sequential):**
```java
// OrderPlacementSaga.java:278
List<InventoryReservation> reservations = new ArrayList<>();
for (OrderItem item : command.getItems()) {
    InventoryReservation r = inventoryPort.occupy(...).join();
    if (r == null) { releaseAll(reservations); throw ...; }
    reservations.add(r);
}
// 3 SKUs = 3x latency
```

**Modern (parallel):**
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    List<Subtask<InventoryReservation>> subtasks = command.getItems().stream()
        .map(item -> scope.fork(() -> inventoryPort.occupy(
            new ReservationRequest(item.getSku(), item.getQuantity(), orderId))))
        .toList();

    scope.join();
    scope.throwIfFailed();

    List<InventoryReservation> reservations = subtasks.stream()
        .map(Subtask::get)
        .toList();
    // 3 SKUs ≈ 1x latency (并发执行)
}
```

**Benefit:** Order-level latency reduced from `N × inventory_p95` to `inventory_p95`.
**Risk:** Low — StructuredTaskScope is JDK 21 stable API.

---

#### US-5.3: ProblemDetail for error responses

**Current:**
```java
// RestExceptionHandler.java — ad-hoc Map
Map<String, Object> body = new HashMap<>();
body.put("error", "Duplicate order");
body.put("traceId", traceId);
return ResponseEntity.status(409).body(body);
```

**Modern (RFC 9457):**
```java
@ExceptionHandler(DuplicateOrderException.class)
public ProblemDetail handleDuplicate(DuplicateOrderException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Duplicate Order");
    pd.setProperty("traceId", resolveTraceId());
    return pd;
}
```

**Response format change:**
```json
// Before
{"error": "Duplicate order", "traceId": "abc123"}

// After (RFC 9457)
{
  "type": "about:blank",
  "title": "Duplicate Order",
  "status": 409,
  "detail": "Order with idempotency key order-001 already exists",
  "instance": "/api/v1/orders",
  "traceId": "abc123"
}
```

**Benefit:** API contract standardization. SpringDoc auto-documents it.
**Risk:** Medium — changes API response format.

---

#### US-5.4: Observe unobserved CompletableFuture

**Current (fire-and-forget):**
```java
@TransactionalEventListener(AFTER_COMMIT)
public void onWmsRequired(WmsInstructionRequiredEvent event) {
    wmsPort.sendInstruction(event.getInstruction())
        .thenCompose(ack -> { ... })
        .exceptionally(ex -> { return null; });
    // 返回值 void — Future 链无人等待
}
```

**Modern (observable):**
```java
@TransactionalEventListener(AFTER_COMMIT)
public void onWmsRequired(WmsInstructionRequiredEvent event) {
    CompletableFuture<Void> sagaStep = wmsPort.sendInstruction(...)
        .thenCompose(ack -> { ... })
        .exceptionally(ex -> { ... });

    sagaStep.whenComplete((res, ex) -> {
        if (ex != null) {
            log.error("Saga step WMS_PHASE failed unobserved: {}", ex.getMessage());
        }
    });
}
```

**Benefit:** No more silently lost saga failures. Enables alerting.
**Risk:** Low — additive change.

---

### 🔴 P1 — Sprint 6: Virtual Threads

#### US-6.1: Enable virtual threads

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

**No code changes** — Spring Boot 3.4 auto-configures Tomcat + task executor.

**Risk:** Low-Medium — verify HikariCP pool sizing.

---

#### US-6.2: Unify HTTP clients to RestClient

**Current:** WebClient (reactive) + RestTemplate (blocking) → both bridge to `CompletableFuture`
**Target:** RestClient (blocking, virtual-thread-optimized) → no bridge needed

```java
// New RestClient adapter
@Component
public class InventoryRestAdapter implements InventoryPort {
    private final RestClient restClient;

    public InventoryReservation occupy(ReservationRequest request) {
        return restClient.post()
            .uri("/api/inventory/reserve")
            .body(request)
            .retrieve()
            .body(InventoryApiResponse.class)
            .toDomain();
        // 阻塞但廉价 — 跑在虚拟线程上
    }
}
```

**Changes:**
- Replace WebClient → RestClient in `InventoryRestAdapter`, `TmsRestAdapter`
- Remove `WmsRestTemplateAdapter` (redundant after unification)
- Simplify `OrderPlacementSaga`: no `.join()`, no `CompletableFuture` bridges
- Remove Reactor Context propagation backlog item (no longer needed)

**Risk:** High — touches 3 adapters + saga. Requires regression test of all BDD scenarios.

---

#### US-6.3: Replace manual OTel spans with Observation framework

```java
// Before: 12 lines of manual span management
// After: 1 annotation
@Observed(name = "inventory.occupy")
public InventoryReservation occupy(ReservationRequest request) {
    return restClient.post()....toDomain();
}
```

**Risk:** Medium — existing span attributes (`sku`, `success`) need ObservationFilter.

---

### 🟢 P3 — Backlog

| Item | Technology | Status |
|------|-----------|--------|
| ScopedValue for traceId | JDK 21 preview API | Monitor JDK releases |
| Declarative HTTP interfaces | `@HttpExchange` | Wait for RestClient unification |

---

## Sprint Roadmap Integration

```
Sprint 4 (v0.4.0-beta)    →    Sprint 5 (v0.5.0-rc.1)    →    Sprint 6 (v0.6.0-rc.2)
┌──────────────────┐         ┌────────────────────┐         ┌──────────────────────┐
│ @Observed         │         │ Modern Java Basics  │         │ Virtual Threads       │
│ Server properties │         │                     │         │                       │
│ Documentation     │         │ Records (6 files)   │         │ Enable VT             │
└──────────────────┘         │ StructuredTaskScope │         │ Unify to RestClient   │
                              │ ProblemDetail       │         │ Observation for OTel  │
                              │ Observe CF          │         │                       │
                              └────────────────────┘         │  (Removes Reactor     │
                                                               │   Context backlog)   │
                                                               └──────────────────────┘
```

## Decision Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-06-23 | Reactor Context item removed from Sprint 5 | Virtual threads eliminate the need; moved to Sprint 6 |
| 2026-06-23 | Feign support removed permanently | Technology deprecated |
| 2026-06-23 | RestTemplate kept until Sprint 6 | Low priority until virtual threads enabled |