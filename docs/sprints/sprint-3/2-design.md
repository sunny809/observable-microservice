# Technical Design — Sprint 3: Internal Transaction Observability

**Author:** Tech-Lead
**Date:** 2026-06-23
**Status:** Approved

---

## 1. Summary

In v0.2.0, the project has perimeter observability (HTTP inbound + outbound per-call metrics)
but zero visibility into **internal saga step timing**. Additionally, `OrderMetrics` is declared
but never invoked (dead code).

This sprint introduces step-level duration tracking, async gap measurement, ECS structured logging,
and per-SKU span attributes — all without breaking hexagonal architecture or adding AOP dependencies.

## 2. Related User Stories

- [US-3.1](1-backlog.md) — Fix dead OrderMetrics code
- [US-3.2](1-backlog.md) — Extend SagaLogPort with timing methods
- [US-3.3](1-backlog.md) — Step-level saga timers
- [US-3.4](1-backlog.md) — Async gap measurement
- [US-3.5](1-backlog.md) — ECS structured logging
- [US-3.6](1-backlog.md) — Per-SKU span attribute
- [US-3.7](1-backlog.md) — Unit tests for saga step metrics

## 3. Scope of Change

**Modules affected:**

| Module | Changes | US |
|--------|---------|----|
| `order-application` | SagaLogPort new methods; saga call sites | US-3.2, US-3.3, US-3.4 |
| `order-adapter` | OrderMetrics new timers; persistence adapter wiring; controller wiring; InventoryRestAdapter span attr | US-3.1, US-3.2, US-3.6 |
| `order-infrastructure` | logback-spring.xml ECS config | US-3.5 |

**Files to change:**

| File | Change Type | Description |
|------|-------------|-------------|
| `order-application/.../port/out/SagaLogPort.java` | Modify | Add `recordStep(4args)` overload + `recordGap()` |
| `order-application/.../service/OrderPlacementSaga.java` | Modify | Wrap SYNC/WMS/TMS phases with timing; add gap measurement |
| `order-adapter/.../metrics/OrderMetrics.java` | Modify | Add `recordSagaStep()` + `recordSagaGap()` methods |
| `order-adapter/.../persistence/SagaLogPersistenceAdapter.java` | Modify | Forward timing to Micrometer + ECS log |
| `order-adapter/.../rest/OrderController.java` | Modify | Inject OrderMetrics, call recordOrderPlaced/Failed |
| `order-adapter/.../outbound/inventory/InventoryRestAdapter.java` | Modify | Add `span.setAttribute("sku", ...)` |
| `order-infrastructure/.../logback-spring.xml` | Modify | ECS field name mapping |

## 4. Architecture Decision

### Decision: Extend SagaLogPort (not inject OrderMetrics into saga)

| Option | Pros | Cons | Selected? |
|--------|------|------|-----------|
| Extend SagaLogPort (domain port) | Clean hex arch, no circular dep, reuses existing injection point | Port interface gets an additional concern (timing) | **✓** |
| Inject OrderMetrics into saga | Simple, direct | Creates circular dependency (adapter→application→adapter), breaks hex arch | ✗ |
| Use `@Observed` AOP annotation | Zero boilerplate in saga | Cannot wrap CompletableFuture async boundaries; can't measure gap; needs new o11y-kit module | ✗ |

**Rationale:** The SagaLogPort extension follows the existing port/adapter pattern.
The saga already calls `sagaLogPort.recordStep()` — adding duration to the same call
is the minimal-change path. Metrics recording happens in the adapter implementation,
keeping the domain layer free of Micrometer.

### Decision: OTel span attribute for per-SKU (not Prometheus tag)

| Option | Pros | Cons | Selected? |
|--------|------|------|-----------|
| OTel span attribute `sku` | Zero cardinality impact on Prometheus; queryable in Jaeger | Requires Jaeger UI to view | **✓** |
| Prometheus metric tag `sku` | Directly queryable in Grafana | Creates N time series per SKU (high cardinality) | ✗ |

## 5. Interface / API Changes

### SagaLogPort — New Methods

```java
public interface SagaLogPort {
    // Existing (unchanged)
    void recordStep(String orderId, String step, String detail);
    void recordCompensation(String orderId, String reservationId, String reason);

    // New — overload with timing
    void recordStep(String orderId, String step, String detail,
                    long durationMs, String outcome);

    // New — explicit gap measurement
    void recordGap(String orderId, String gapName, long durationMs);
}
```

### OrderMetrics — New Methods

```java
public void recordSagaStep(String step, long durationMs, String outcome);
public void recordSagaGap(String gap, long durationMs);
```

## 6. Data Flow

```
OrderPlacementSaga
  │
  │ calls sagaLogPort.recordStep(orderId, "SYNC_PHASE", ..., durationMs, outcome)
  ▼
SagaLogPersistenceAdapter (implements SagaLogPort)
  │
  ├──▶ SagaLogJpaRepository.save()         (DB log — existing)
  │
  ├──▶ orderMetrics.recordSagaStep()        (Micrometer Timer — new)
  │      └──▶ MeterRegistry.Timer("saga.step.duration", "step", "SYNC_PHASE", ...)
  │
  └──▶ log.atInfo().addKeyValue(...)        (ECS JSON log — new)
         └──▶ stdout → Filebeat → Logstash → Elasticsearch
```

**Gap measurement flow:**
```
@TransactionalEventListener(AFTER_COMMIT)
void onWmsRequired(WmsInstructionRequiredEvent event) {
    long gap = System.currentTimeMillis() - commitTimestamp;
    sagaLogPort.recordGap(event.getOrderId(), "POST_COMMIT_TO_WMS", gap);
    // ... then wrap WMS_PHASE timing ...
}
```

## 7. Compatibility & Migration

- **Backward compatible?** Yes — old 3-arg `recordStep()` overload unchanged
- **Migration required?** No
- **Configuration changes?** `logback-spring.xml`: `customFields` format changes (non-breaking, old consumers still see same fields plus new nested ones)

## 8. Testing Strategy

| Level | Scope | Approach |
|-------|-------|----------|
| Unit | SagaLogPersistenceAdapter | Mock OrderMetrics, verify metrics recording |
| Unit | OrderPlacementSaga | Verify sagaLogPort.recordStep called with expected duration values |
| Integration | Full Spring context | Verify `/actuator/prometheus` returns new metrics after order placement |
| BDD | — | No new BDD scenarios needed (covered by existing place_order.feature paths) |

## 9. Risks & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| StructuredArguments not output in JSON | Low | High | Verify with LogstashEncoder + customFields in dev |
| Saga async callback timing inaccurate | Medium | Medium | Use System.currentTimeMillis() at entry; document that CompletableFuture scheduling adds small overhead |
| Test count regression from MIGRATION.md | Low | Low | Run full `mvn verify` before merge |