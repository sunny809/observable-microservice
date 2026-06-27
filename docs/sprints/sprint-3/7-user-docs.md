# User Documentation — Sprint 3: Internal Transaction Observability

**Author:** Technical Writer
**Date:** YYYY-MM-DD
**Status:** Draft

**Target audience:** Spring Boot developers, DevOps/SRE engineers operating the order service

---

## 1. Feature Overview

### What Problem Does This Solve?

Before Sprint 3, you could see **what** HTTP calls the service made (inventory occupy, WMS send, TMS send)
and their individual durations — but you could NOT see:

- How long did the **entire synchronous phase** (idempotency check + inventory loop + DB save) take?
- Did the 2-second delay come from WMS processing or from idle time between async callbacks?
- Was a slow order caused by a specific inventory SKU?
- Did the compensating transaction (release) add significant latency?

Sprint 3 makes **every internal step of a saga transaction visible** across Prometheus, Jaeger, and ELK.

### What Was Delivered?

| Feature | Module | Since | User Story |
|---------|--------|-------|------------|
| Step-level saga duration timers | `order-adapter` + `order-application` | v0.3.0-beta | US-3.3 |
| Async gap measurement | `order-application` | v0.3.0-beta | US-3.4 |
| ECS structured logging | `order-infrastructure` | v0.3.0-beta | US-3.5 |
| Per-SKU OTel span attribute | `order-adapter` | v0.3.0-beta | US-3.6 |
| Fixed: business metrics wiring | `order-adapter` | v0.3.0-beta | US-3.1 |

---

## 2. Quick Start

No configuration changes needed to use the new metrics. If you already have the application
running with `o11y-kit-spring-boot-starter`, the new metrics are automatically available.

### Verify It Works (Prometheus)

```bash
# After placing an order, check new metrics appear
curl -s http://localhost:8080/actuator/prometheus | grep -E "saga_|orders_|inventory_"

# Expected output:
# saga_step_duration_seconds_count{step="SYNC_PHASE",outcome="success"}
# saga_step_duration_seconds_count{step="WMS_PHASE",outcome="success"}
# saga_step_duration_seconds_count{step="TMS_PHASE",outcome="success"}
# saga_gap_duration_seconds_count{gap="POST_COMMIT_TO_WMS"}
# orders_placed_total{status="CREATED"}
# orders_failed_total{reason="DUPLICATE_ORDER"}
```

### Verify It Works (ECS Logs)

```bash
# After placing an order, check stdout for structured log events
docker logs order-service 2>&1 | grep saga.step

# Expected:
# {"event":{"action":"saga.step.begin"},"saga":{"step":"SYNC_PHASE","order_id":"..."}}
# {"event":{"action":"saga.step.end"},"saga":{"step":"SYNC_PHASE","outcome":"success"},"event.duration":...}
# {"event":{"action":"saga.gap"},"saga":{"gap":"POST_COMMIT_TO_WMS"}}
```

---

## 3. Metrics Reference

### Saga Step Duration

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `saga.step.duration` | Timer | `step`, `outcome` | Duration of a single saga phase |

**Tags:**

| Tag | Values | Description |
|-----|--------|-------------|
| `step` | `SYNC_PHASE`, `WMS_PHASE`, `TMS_PHASE`, `COMPENSATION` | Which saga phase |
| `outcome` | `success`, `failure`, `compensation` | Whether the step completed normally or triggered compensation |

**Example PromQL:**

```promql
# p95 latency per saga step (last 5 minutes)
histogram_quantile(0.95,
  sum(rate(saga_step_duration_seconds_bucket[5m])) by (le, step)
)

# Average duration per step
avg by (step) (saga_step_duration_seconds_count)
```

### Saga Gap Duration

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `saga.gap.duration` | Timer | `gap` | Idle/staging time between saga steps |

**Tags:**

| Tag | Values | Description |
|-----|--------|-------------|
| `gap` | `POST_COMMIT_TO_WMS`, `WMS_ACKED_TO_PICKED`, `PICKED_TO_TMS` | Which transition was measured |

**What each gap represents:**

| Gap | Measures | Typical Range |
|-----|----------|---------------|
| `POST_COMMIT_TO_WMS` | Transaction commit + event dispatch + Reactor scheduling | 1-50ms |
| `WMS_ACKED_TO_PICKED` | WMS external processing time (client-side idle) | 1-30s (depends on WMS) |
| `PICKED_TO_TMS` | Event publishing + TMS dispatch scheduling | 1-50ms |

### Business Metrics (Fixed from Dead Code)

These metrics existed in code but were never actually recorded. Sprint 3 fixed the wiring:

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `orders.placed` | Counter | `status` | Total orders placed by final status |
| `orders.failed` | Counter | `reason` | Total failed orders by reason |
| `inventory.reservation` | Counter | `result` | Inventory reservation attempts by outcome |
| `saga.duration` | Timer | `outcome` | Total end-to-end saga duration |

**Tags:**

| Metric | Tag | Values |
|--------|-----|--------|
| `orders.placed` | `status` | `CREATED`, `WMS_ACKED`, `WMS_PICKED`, `TMS_DISPATCHED` |
| `orders.failed` | `reason` | `DUPLICATE_ORDER`, `INSUFFICIENT_INVENTORY` |
| `inventory.reservation` | `result` | `success`, `failure` |
| `saga.duration` | `outcome` | `success`, `compensation`, `failure` |

---

## 4. ECS Structured Log Reference

### Saga Step Events

#### Step Begin

```json
{
  "@timestamp": "2026-06-23T12:00:00.000+08:00",
  "log.level": "INFO",
  "event.action": "saga.step.begin",
  "saga.order_id": "ord-42",
  "saga.step": "SYNC_PHASE",
  "trace.id": "abc123def456",
  "message": "Saga step SYNC_PHASE started for order ord-42"
}
```

#### Step End

```json
{
  "@timestamp": "2026-06-23T12:00:00.000+08:00",
  "log.level": "INFO",
  "event.action": "saga.step.end",
  "event.duration": 85000000,
  "saga.order_id": "ord-42",
  "saga.step": "SYNC_PHASE",
  "saga.outcome": "success",
  "trace.id": "abc123def456",
  "message": "Saga step SYNC_PHASE completed: 85ms"
}
```

#### Gap Record

```json
{
  "@timestamp": "2026-06-23T12:00:10.000+08:00",
  "log.level": "INFO",
  "event.action": "saga.gap",
  "event.duration": 2100000000,
  "saga.order_id": "ord-42",
  "saga.gap": "WMS_ACKED_TO_PICKED",
  "trace.id": "abc123def456",
  "message": "Saga gap WMS_ACKED_TO_PICKED: 2100ms"
}
```

### Elasticsearch / Kibana Queries

```json
// Find all saga steps for a specific order
GET order-logs/_search
{
  "query": {
    "bool": {
      "must": [
        {"term": {"event.action": "saga.step.end"}},
        {"term": {"saga.order_id": "ord-42"}}
      ]
    }
  }
}

// Find all slow WMS phases (>1s)
GET order-logs/_search
{
  "query": {
    "bool": {
      "must": [
        {"term": {"saga.step": "WMS_PHASE"}},
        {"range": {"event.duration": {"gte": 1000000000}}}
      ]
    }
  }
}
```

---

## 5. Jaeger Trace Attributes

### Per-SKU Span Attribute

The `inventory.occupy` span now includes the SKU being reserved:

| Span Name | Attribute | Type | Example | Since |
|-----------|-----------|------|---------|-------|
| `inventory.occupy` | `sku` | string | `"SKU-1"` | v0.3.0-beta |
| `inventory.occupy` | `item.quantity` | int | `2` | v0.3.0-beta |

**Jaeger query to filter by SKU:**

```
{resource.service.name="order-service"} && {sku="SKU-1"}
```

This returns all inventory occupy calls for SKU-1, allowing you to compare latency across items.

**Important:** SKU is stored as a span attribute only (not as a Prometheus metric tag).
This prevents high-cardinality time series explosion in Prometheus.
Use Jaeger for per-SKU analysis, not Grafana dashboards.

---

## 6. Configuration Reference

No new configuration properties were added in Sprint 3.
The existing `o11y.kit.*` properties continue to work unchanged.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `o11y.kit.client.enabled` | boolean | `true` | Enable/disable outbound HTTP observation |
| `o11y.kit.client.metrics.enabled` | boolean | `true` | Enable/disable client-side Micrometer metrics |

---

## 7. Use Cases

### Use Case 1: A Customer Complains About a Slow Order

**Symptom:** An order took 4 seconds to complete. The HTTP response returned in 200ms,
but the saga took 4 seconds.

**Diagnostic steps:**

1. Find the trace ID from the HTTP response header `X-Trace-Id`
2. Query Prometheus: `saga_step_duration_seconds{step="WMS_PHASE"}` → p95 = 3.5s
3. Query Prometheus: `saga_gap_duration_seconds{gap="WMS_ACKED_TO_PICKED"}` → average = 2s
4. **Conclusion:** The WMS service takes 3.5s on average, and the gap between WMS ack and picking completion adds another 2s. Total = 5.5s, which matches the complaint.

**Remediation:** Investigate WMS service performance. Consider async timeout reduction.

### Use Case 2: Monitoring Inventory Reservation Health

**Symptom:** Orders are failing with "insufficient inventory" unexpectedly.

**Diagnostic steps:**

1. Query: `orders_failed_total{reason="INSUFFICIENT_INVENTORY"}`
2. Query: `inventory_reservation_total{result="failure"}` to see per-item failure rate
3. Drill into Jaeger: `inventory.occupy` spans with `sku` attribute to find which SKUs fail most often

**Remediation:** Restock the affected SKUs or investigate inventory service availability.

### Use Case 3: Debugging Compensation Path

**Symptom:** An order was rejected, but the compensation (inventory release) took unusually long.

**Diagnostic steps:**

1. Find the trace ID for the rejected order
2. Check `saga_step_duration_seconds{step="COMPENSATION",outcome="compensation"}`
3. Check individual `inventory.release` spans in Jaeger

**Remediation:** If release is slow, check inventory service circuit breaker status.

---

## 8. Examples

### Grafana Dashboard PromQL Queries

```promql
# Total orders per minute
sum(rate(orders_placed_total[5m]))

# Failed order rate
sum(rate(orders_failed_total[5m]))

# Saga step p50 latency
histogram_quantile(0.50,
  sum(rate(saga_step_duration_seconds_bucket[5m])) by (le, step)
)

# Saga step p99 latency
histogram_quantile(0.99,
  sum(rate(saga_step_duration_seconds_bucket[5m])) by (le, step)
)

# Gap durations over time
avg(saga_gap_duration_seconds_count) by (gap)

# Saga success rate
sum by (outcome) (saga_duration_seconds_count)
```

### Curl Examples

```bash
# Place an order
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId":"cust-1","idempotencyKey":"test-001","items":[{"sku":"SKU-1","quantity":2}]}'

# Check saga metrics
curl -s http://localhost:8080/actuator/prometheus | grep saga_

# Check order metrics
curl -s http://localhost:8080/actuator/prometheus | grep orders_

# Check inventory metrics
curl -s http://localhost:8080/actuator/prometheus | grep inventory_
```

---

## 9. Known Limitations

| Limitation | Workaround | Planned Fix |
|-----------|-----------|-------------|
| Async gap measurement depends on `System.currentTimeMillis()` at event handler entry; sub-ms precision not guaranteed | Use Jaeger tracing for microsecond-precision analysis | v0.5.0 (Reactor Context propagation) |
| `event.duration` in ECS logs is in nanoseconds but precision is millisecond | Acceptable for service-level SLA monitoring | No change needed |
| Per-SKU analysis requires Jaeger UI; not available in Prometheus/Grafana | Use Jaeger's tag-based search | Intentional design (cardinality protection) |