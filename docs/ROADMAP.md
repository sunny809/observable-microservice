# Project Roadmap

> This roadmap covers both the **order-demo** reference application and the **o11y-kit** SDK.
> The two projects co-evolve: o11y-kit provides the instrumentation primitives, order-demo
> demonstrates their use in a realistic Saga-pattern microservice.

---

## Release Overview

| Version | Focus | o11y-kit | order-demo | Target |
|---------|-------|----------|------------|--------|
| v0.1.0-alpha | MVP: WebClient + Controller | ✅ 18 tests | ✅ 84 tests | 2026-07 |
| v0.1.1-alpha | Bugfix: 13 code review fixes | ✅ 24 tests | ✅ 96 tests | 2026-07 |
| v0.2.0-alpha | RestTemplate/RestClient + static analysis | ✅ 82 tests | ✅ 90 tests | 2026-08 |
| **v0.3.0-beta** | **Internal transaction latency** | **—** | **Planned** | **2026-09** |
| v0.4.0-beta | `@Observed` annotation + server properties | ✅ 90+ tests | In progress | 2026-10 |
| v0.5.0-rc.1 | Modern Java: records, structured concurrency, ProblemDetail | Planned | Planned | 2026-11 |
| v0.6.0-rc.2 | Virtual threads + RestClient unification + Observation | Planned | Planned | 2026-12 |
| v1.0.0-GA | Maven Central + SonarCloud + docs site | Planned | Planned | Q4 2026 |

---

## v0.3.0-beta — Internal Transaction Observability

### Theme

Make **every internal step of a transaction visible** in Prometheus + Jaeger + ELK.
Stakeholders can answer: "Why did this order take 3 seconds? Was it inventory, WMS,
or idle time between steps?"

### Key Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Timing mechanism | Extend `SagaLogPort` (domain port + adapter) | Avoids injecting Micrometer into domain layer; reuses existing port/adapter pattern |
| Per-SKU tracking | OTel span attribute only, not Prometheus tag | Prevents high-cardinality time series explosion |
| Log format | Elastic Common Schema (ECS) | Zero transformation needed in DevOps' Logstash pipeline |
| `@Observed` annotation | Deferred to v0.4.0 | AOP cannot wrap async `CompletableFuture` boundaries; 6 lines of manual timing in saga is insufficient ROI for AOP |

### Scope

#### ✅ Included (P0-P2)

| # | Theme | Priority | Effort | Owner |
|---|-------|----------|--------|-------|
| 1 | Wire dead `OrderMetrics` into saga flow | **P0** | S | order-demo |
| 2 | Extend `SagaLogPort` with timing + gap methods | **P0** | S | order-demo |
| 3 | Step-level saga timers (SYNC/WMS/TMS/COMPENSATION) | **P0** | M | order-demo |
| 4 | Async gap measurement (POST_COMMIT_TO_WMS, WMS_ACKED_TO_PICKED) | **P1** | M | order-demo |
| 5 | ECS structured logging at step boundaries | **P1** | S | order-demo |
| 6 | Per-SKU span attribute in inventory occupy | **P2** | XS | order-demo |

#### ❌ Explicitly Excluded

| Feature | Reason | Moved To |
|---------|--------|----------|
| `@Observed` annotation | AOP can't wrap async boundaries; 6-line manual timing is already minimal | v0.4.0-beta |
| DB operation timing (`repository.duration`) | Separate concern (DB performance), not "outbound call gaps" | Backlog |
| Feign support | Technology deprecated per o11y-kit FAQ | **Removed permanently** |
| NullAway | Static analysis, unrelated to observability | v0.4.0-beta |
| `o11y.kit.server.*` properties | SDK infrastructure, not latency data | v0.4.0-beta |

### Architecture Change

```
v0.2.0 (current)                          v0.3.0 (target)
┌──────────────────────┐                 ┌──────────────────────────────┐
│ SagaLogPort          │                 │ SagaLogPort                  │
│  recordStep()        │                 │  recordStep()                │
│  recordCompensation()│                 │  recordStep(duration,result) │ ← 新增重载
└──────────┬───────────┘                 │  recordGap()                 │ ← 新增方法
           │                             └──────────┬───────────────────┘
           ▼ implement                              ▼ implement
┌──────────────────────┐                 ┌──────────────────────────────┐
│ SagaLogPersistence   │                 │ SagaLogPersistenceAdapter    │
│ Adapter              │                 │  ─ DB log (原有)              │
│  ─ 只写 DB           │                 │  ─ Micrometer metrics (新增)  │
└──────────────────────┘                 │  ─ ECS structured log (新增)  │
                                         └──────────────────────────────┘
```

New metrics exposed at `/actuator/prometheus`:

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `saga.step.duration` | Timer | `step`, `outcome` | Per-phase saga step latency |
| `saga.gap.duration` | Timer | `gap` | Idle time between saga phases |
| `orders.placed` | Counter | `status` | (fixed from dead code) |
| `orders.failed` | Counter | `reason` | (fixed from dead code) |
| `inventory.reservation` | Counter | `result` | (fixed from dead code) |
| `saga.duration` | Timer | `outcome` | (fixed from dead code) |

### Deliverables

After v0.3.0, stakeholders can:

- **Prometheus/Grafana**: Real-time p50/p95/p99 per saga step
- **Jaeger**: Waterfall trace with per-SKU span attributes
- **Elasticsearch/Kibana**: Query `event.action:saga.step.*` for step-level duration analysis

---

## v0.4.0-beta — SDK Maturity

**Status:** Current sprint

- `@Observed` annotation (AOP method-level observation)
- `o11y.kit.server.*` configuration properties (enabled, exclude-patterns, metrics.enabled)
- User documentation: `@Observed` usage guide, config reference

---

## v0.5.0-rc.1 — Modern Java Basics

**Theme:** Adopt JDK 21 stable features for cleaner code

- **Records** (6 files): `WmsShipmentInstruction`, `TmsShipmentInstruction`, `OrderItem`, `PlaceOrderCommand`, `OrderResponse`, `WmsInstructionRequiredEvent`
- **StructuredTaskScope**: Parallelize `reserveAllItems()` inventory loop (N× → 1× latency)
- **ProblemDetail** (RFC 9457): Standardize error response format
- **Observable futures**: Log unobserved saga CompletableFuture failures
- **Reactor Context propagation item REMOVED** — deferred to Sprint 6 (solved by virtual threads)

> Full design: [MODERNIZATION_PLAN.md](reference/MODERNIZATION_PLAN.md)

---

## v0.6.0-rc.2 — Virtual Threads & HTTP Unification

**Theme:** Transform architecture for virtual-thread-native execution

- **Enable virtual threads** (`spring.threads.virtual.enabled=true`)
- **Unify HTTP clients**: Replace WebClient + RestTemplate → RestClient (single synchronous client)
- **Observation framework**: Replace manual OTel spans with `@Observed` + `ObservationFilter`
- **Simplify saga**: Remove `.join()`, `CompletableFuture` bridges, `TransactionTemplate` workarounds
- **Removed backlog**: Reactor Context propagation (solved by virtual threads)

> Full design: [MODERNIZATION_PLAN.md](reference/MODERNIZATION_PLAN.md)

---

## v1.0.0-GA — Production Release

- Maven Central publication (`io.o11y.kit`)
- SonarCloud quality gate
- Documentation site
- SLAs: 90%+ line coverage, zero SpotBugs/Checkstyle violations