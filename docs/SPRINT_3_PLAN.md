# Sprint 3 Plan — Internal Transaction Observability

> **⚠️ DEPRECATED**: This document has been superseded by the sprint-based documentation framework.
> See [`sprints/sprint-3/`](sprints/sprint-3/) for the canonical Sprint 3 artifacts:
> - [Backlog](sprints/sprint-3/1-backlog.md) — User Stories + Acceptance Criteria
> - [Design](sprints/sprint-3/2-design.md) — Technical design
> - [Test Design](sprints/sprint-3/2-test-design.md) — Test scenarios
> - [DoD](sprints/sprint-3/5-dod.md) — Definition of Done checklist

**Version:** v0.3.0-beta

**Theme:** Make every internal step of a saga transaction visible in Prometheus + Jaeger + ELK.

**Duration:** 3 days engineering (actual); 2026-09 target release.

**Total tickets:** 7 (3 P0, 2 P1, 2 P2)

---

## Epic: Internal Latency Breakdown

### Summary

The stakeholder wants to know the latency of a single transaction inside the microservice:
- How long did the inbound receive take? (already exists: `o11y.server.requests`)
- How long between outbound calls (inventory → WMS → TMS)? (missing)
- How long were the idle gaps between async steps? (missing)

Additionally, the DevOps team needs ECS-compatible structured logs for ELK consumption.

### Key Constraints

1. **Architecture**: `OrderMetrics` (Micrometer, in `order-adapter`) must NOT be injected into `OrderPlacementSaga` (in `order-application`), as this creates a circular dependency. Solution: extend `SagaLogPort` interface.
2. **Cardinality**: Per-SKU latency tracked via OTel span attribute **only** (Jaeger), never as a Prometheus metric tag.
3. **Zero o11y-kit changes**: All work is within order-demo's own modules.

---

## Backlog

### Sprint 3-1: [P0] Fix dead code — Wire OrderMetrics into saga flow

**Status:** Not started

**Files:**
- `order-adapter/src/main/java/.../metrics/OrderMetrics.java`
- `order-adapter/src/main/java/.../rest/OrderController.java`

**Description:**
`OrderMetrics` defines 4 business metrics (`orders.placed`, `orders.failed`, `inventory.reservation`, `saga.duration`) but **zero call sites exist** anywhere in the codebase. This is dead code — metrics are registered but always 0.

**Tasks:**
1. Inject `OrderMetrics` into `OrderController`
2. Call `recordOrderPlaced(status)` on successful `placeOrderUseCase.placeOrder()`
3. Call `recordOrderFailed(reason)` on `DuplicateOrderException` and `InsufficientInventoryException`

**Acceptance criteria:**
- After placing one order, `/actuator/prometheus` shows `orders_placed_total{status="CREATED"} 1`
- After a duplicate request, `orders_failed_total{reason="DUPLICATE_ORDER"} 1`

**Effort:** S (0.5 day)

---

### Sprint 3-2: [P0] Extend SagaLogPort with timing methods

**Status:** Not started

**Files:**
- `order-application/src/main/java/.../port/out/SagaLogPort.java` (interface)
- `order-adapter/src/main/java/.../persistence/SagaLogPersistenceAdapter.java` (implementation)
- `order-adapter/src/main/java/.../metrics/OrderMetrics.java`

**Description:**
The `SagaLogPort` interface needs two new methods to carry timing data from the saga to the adapter layer, keeping the domain layer free of Micrometer dependencies.

**New API:**

```java
public interface SagaLogPort {
    // Existing (unchanged)
    void recordStep(String orderId, String step, String detail);
    void recordCompensation(String orderId, String reservationId, String reason);

    // New — overloading with timing
    void recordStep(String orderId, String step, String detail,
                    long durationMs, String outcome);

    // New — explicit gap measurement
    void recordGap(String orderId, String gapName, long durationMs);
}
```

**Implementation in `SagaLogPersistenceAdapter`:**

```java
@Override
public void recordStep(String orderId, String step, String detail,
                       long durationMs, String outcome) {
    // 1. DB log (existing behavior)
    repository.save(new SagaLogEntity(orderId, step, detail, LocalDateTime.now()));

    // 2. Micrometer timer (new)
    orderMetrics.recordSagaStep(step, durationMs, outcome);

    // 3. ECS structured log (new)
    log.atInfo()
        .setMessage("Saga step {} completed: {}ms")
        .addArgument(step).addArgument(durationMs)
        .addKeyValue("event.action", "saga.step.end")
        .addKeyValue("event.duration", durationMs * 1_000_000)
        .addKeyValue("saga.order_id", orderId)
        .addKeyValue("saga.step", step)
        .addKeyValue("saga.outcome", outcome)
        .log();
}

@Override
public void recordGap(String orderId, String gapName, long durationMs) {
    // 1. Micrometer timer
    orderMetrics.recordSagaGap(gapName, durationMs);

    // 2. ECS structured log
    log.atInfo()
        .setMessage("Saga gap {}: {}ms")
        .addArgument(gapName).addArgument(durationMs)
        .addKeyValue("event.action", "saga.gap")
        .addKeyValue("event.duration", durationMs * 1_000_000)
        .addKeyValue("saga.order_id", orderId)
        .addKeyValue("saga.gap", gapName)
        .log();
}
```

**Acceptance criteria:**
- `SagaLogPort` compiles with new overloaded methods
- Old callers (3-arg) continue to work unchanged
- `SagaLogPersistenceAdapter` forwards timing data to both Micrometer and structured log

**Effort:** M (1 day)

---

### Sprint 3-3: [P0] Add step-level saga timers

**Status:** Not started

**Files:**
- `order-adapter/src/main/java/.../metrics/OrderMetrics.java` (new timer methods)
- `order-application/src/main/java/.../service/OrderPlacementSaga.java` (call sites)

**Description:**
Wrap the three saga phases with timing using the newly extended `SagaLogPort`. Each phase records duration + outcome.

**New metrics to add in `OrderMetrics`:**

```java
public void recordSagaStep(String step, long durationMs, String outcome) {
    Timer.builder("saga.step.duration")
        .tag("step", step)
        .tag("outcome", outcome)
        .description("Duration of individual saga step")
        .register(meterRegistry)
        .record(durationMs, TimeUnit.MILLISECONDS);
}

public void recordSagaGap(String gap, long durationMs) {
    Timer.builder("saga.gap.duration")
        .tag("gap", gap)
        .description("Idle gap duration between saga steps")
        .register(meterRegistry)
        .record(durationMs, TimeUnit.MILLISECONDS);
}
```

**Call sites in `OrderPlacementSaga`:**

```java
// Sync phase — synchronous, inside HTTP request thread
public OrderPlacedResult placeOrder(PlaceOrderCommand command) {
    long start = System.currentTimeMillis();
    // ... existing logic (unchanged) ...
    sagaLogPort.recordStep(orderId, "SYNC_PHASE", logMsg,
        System.currentTimeMillis() - start, "success");
    return result;
}

// WMS phase — async, in CompletableFuture callback
void onWmsRequired(WmsInstructionRequiredEvent event) {
    long start = System.currentTimeMillis();
    // ... existing logic (unchanged) ...
    // Duration recorded inside .thenCompose() or .exceptionally()
}

// TMS phase — async, in CompletableFuture callback
void onTmsRequired(TmsInstructionRequiredEvent event) {
    long start = System.currentTimeMillis();
    // ... existing logic (unchanged) ...
}
```

**Acceptance criteria:**
- After a complete saga cycle, `/actuator/prometheus` shows non-zero values for:
  - `saga_step_duration_seconds{step="SYNC_PHASE"}`
  - `saga_step_duration_seconds{step="WMS_PHASE"}`
  - `saga_step_duration_seconds{step="TMS_PHASE"}`
- Failed/compensated paths show `outcome="failure"` or `outcome="compensation"`

**Effort:** M (1 day)

---

### Sprint 3-4: [P1] Async gap measurement

**Status:** Not started

**Files:**
- `order-application/src/main/java/.../service/OrderPlacementSaga.java`

**Description:**
The idle time between "HTTP 201 response sent" and "WMS callback received" is currently invisible. Add explicit gap measurement at event handler entry points.

**Measured gaps:**

| Gap | Measured where | What it represents |
|-----|---------------|-------------------|
| `POST_COMMIT_TO_WMS` | Start of `onWmsRequired()` | Transaction commit + event dispatch + Reactor scheduling overhead |
| `WMS_ACKED_TO_PICKED` | Start of `onWmsPickingCompleted()` | WMS external processing time (client-side idle) |
| `PICKED_TO_TMS` | Start of `onTmsRequired()` | Event publishing + reactor scheduling |

**Code sketch:**

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onWmsRequired(WmsInstructionRequiredEvent event) {
    // Measure gap between order creation and this handler firing
    sagaLogPort.recordGap(event.getOrderId(), "POST_COMMIT_TO_WMS", ...);
    // ... then measure WMS_PHASE step duration as usual ...
}
```

**Acceptance criteria:**
- `/actuator/prometheus` shows `saga_gap_duration_seconds{gap="POST_COMMIT_TO_WMS"}`
- Gap values are realistic (single-digit ms for event dispatch, seconds for WMS processing)

**Effort:** M (0.5 day)

---

### Sprint 3-5: [P1] ECS structured logging

**Status:** Not started

**Files:**
- `order-infrastructure/src/main/resources/logback-spring.xml`

**Description:**
Adjust the Logstash encoder configuration to output ECS-compatible field names, ensuring DevOps' ELK pipeline can consume saga events without transformation.

**Config change:**

```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <includeContext>false</includeContext>
    <includeMdc>true</includeMdc>
    <customFields>{
        "service":{"name":"${SERVICE_NAME:-order-service}"},
        "ecs":{"version":"8.0.0"}
    }</customFields>
    <fieldNames>
        <timestamp>@timestamp</timestamp>
        <level>log.level</level>
        <logger>log.logger</logger>
        <mdc>mdc</mdc>
    </fieldNames>
</encoder>
```

**ECS field mapping:**

| Existing field | ECS target | Notes |
|---------------|-----------|-------|
| `@timestamp` | `@timestamp` | Unchanged |
| `level` | `log.level` | New nested field |
| `logger_name` | `log.logger` | New nested field |
| `traceId` (MDC) | `trace.id` | Rename in Logstash pipeline |
| `service` | `service.name` | New nested object |
| (new) `event.action` | `event.action` | `saga.step.begin/end` or `saga.gap` |
| (new) `event.duration` | `event.duration` | Nanoseconds per ECS standard |
| (new) `saga.*` | `saga.*` | Custom namespace |

**Acceptance criteria:**
- Log output JSON fields follow ECS naming conventions
- Existing log consumers (non-ECS) continue to work
- Saga step events appear in Kibana with `event.action: saga.step.*`

**Effort:** S (0.5 day)

---

### Sprint 3-6: [P2] Per-SKU span attribute

**Status:** Not started

**Files:**
- `order-adapter/src/main/java/.../outbound/inventory/InventoryRestAdapter.java`

**Description:**
Add SKU information to the existing OTel span for inventory occupy calls. This enables Jaeger-level filtering without adding Prometheus metric cardinality.

**Change:**

```java
// In InventoryRestAdapter.occupy()
Span span = tracer.spanBuilder(SpanNames.INVENTORY_OCCUPY).startSpan();
span.setAttribute("sku", request.getSku());              // ← 1 line added
span.setAttribute("item.quantity", request.getQuantity()); // ← optional
span.setAttribute("inventory.reserve.success", true);    // ← already exists
```

**Query in Jaeger:**
```
{resource.service.name="order-service"} && {sku="SKU-1"}
```
Returns all inventory occupy spans for SKU-1 with their latency distribution.

**Acceptance criteria:**
- Jaeger trace view for an order shows `inventory.occupy` span with `sku="SKU-1"` attribute
- No new Prometheus metrics or metric tags added
- Existing `o11y.client.requests` metric is unaffected

**Effort:** XS (0.2 day)

---

### Sprint 3-7: [P2] Unit tests for saga step metrics

**Status:** Not started

**Files:**
- `order-application/src/test/java/.../service/OrderPlacementSagaTest.java`
- `order-adapter/src/test/java/.../persistence/SagaLogPersistenceAdapterTest.java`

**Description:**
Verify that:
1. `SagaLogPersistenceAdapter.recordStep(duration, outcome)` calls `OrderMetrics.recordSagaStep()`
2. `SagaLogPersistenceAdapter.recordGap()` calls `OrderMetrics.recordSagaGap()`
3. The saga integration test verifies timing is recorded for all three phases

**Test approach:**
- Mock `SagaLogPort` / `OrderMetrics` at the adapter level
- Verify Micrometer interaction with `MockMeterRegistry` assertions
- Existing saga test (`OrderPlacementSagaTest.java`) already mocks `SagaLogPort` — verify the new overload is called with expected values

**Acceptance criteria:**
- All new code paths covered by unit tests
- JaCoCo ≥80% line coverage maintained (or justified exclusions added)

**Effort:** M (1 day)

---

## Effort Summary

| Ticket | Priority | Area | Effort (days) | Dependencies |
|--------|----------|------|---------------|-------------|
| S3-1 Wire OrderMetrics | P0 | order-adapter | 0.5 | None |
| S3-2 Extend SagaLogPort | P0 | order-application + adapter | 1.0 | None |
| S3-3 Step-level timers | P0 | order-application | 1.0 | S3-2 (SagaLogPort) |
| S3-4 Async gap measurement | P1 | order-application | 0.5 | S3-2 (SagaLogPort) |
| S3-5 ECS logging | P1 | order-infrastructure | 0.5 | None |
| S3-6 Per-SKU span attribute | P2 | order-adapter | 0.2 | None |
| S3-7 Unit tests | P2 | order-application + adapter | 1.0 | S3-2, S3-3 |

**Total:** ~3.7 engineering days

**Dependency chain:**
```
S3-1 (no deps) ──────┐
                      ├── S3-3 ──┐
S3-2 (no deps) ──────┘         ├── S3-7
                                │
S3-4 ──(depends on S3-2)───────┘
S3-5 (no deps)
S3-6 (no deps)
```

---

## Verification Checklist

### After Sprint 3, DevOps can verify:

```bash
# 1. Prometheus — all 6 metrics have non-zero values
curl -s http://localhost:8080/actuator/prometheus | grep -E "saga_|orders_|inventory_"

# Expected output:
# saga_step_duration_seconds_count{step="SYNC_PHASE",outcome="success"}  ≥1
# saga_step_duration_seconds_count{step="WMS_PHASE",outcome="success"}   ≥1
# saga_step_duration_seconds_count{step="TMS_PHASE",outcome="success"}   ≥1
# saga_gap_duration_seconds_count{gap="POST_COMMIT_TO_WMS"}              ≥1
# saga_gap_duration_seconds_count{gap="WMS_ACKED_TO_PICKED"}             ≥1
# orders_placed_total{status="CREATED"}                                  ≥1
# orders_failed_total{reason="INSUFFICIENT_INVENTORY"}                   ≥0
# saga_duration_seconds_count{outcome="success"}                         ≥1
```

```bash
# 2. Jaeger — per-SKU span attributes
# Open trace for order , verify:
# └─ inventory.occupy span has attribute: sku="SKU-1"
```

```bash
# 3. Elasticsearch — ECS structured log events
# Query: event.action: "saga.step.*"
# Expected hits:
# {"event.action":"saga.step.begin","saga.step":"SYNC_PHASE","saga.order_id":"ord-42"}
# {"event.action":"saga.step.end",  "saga.step":"SYNC_PHASE","event.duration":85000000}
# {"event.action":"saga.gap",       "saga.gap":"POST_COMMIT_TO_WMS","event.duration":15000000}
```

---

## What's NOT in Sprint 3 (and why)

| Feature | Reason | Future sprint |
|---------|--------|--------------|
| DB operation timing | Separate concern: database performance analysis vs. service call latency | Backlog |
| `@Observed` AOP annotation | Can't wrap async saga boundaries; 6-line manual timing is already minimal | v0.4.0 |
| `o11y.kit.server.*` properties | SDK configuration, not latency data | v0.4.0 |
| NullAway | Static analysis, independent workstream | v0.4.0 |
| Feign support | Technology deprecated; replaced by declarative HTTP interfaces | Removed permanently |
| Declarative HTTP interfaces (`@HttpExchange`) | Strategic replacement for Feign | v0.5.0 |
| OTel root saga span | Jaeger enhancement; metrics (Prometheus) ship first | v0.5.0 |