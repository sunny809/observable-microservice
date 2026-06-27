# User Documentation — Sprint N Feature Guide

**Author:** Technical Writer
**Date:** YYYY-MM-DD
**Status:** Draft / Review / Published

**Target audience:** Developers / DevOps / SRE using this blueprint

---

## 1. Feature Overview

### What Problem Does This Solve?

One-paragraph explanation of the user pain point.

### What Was Delivered?

| Feature | Module | Since |
|---------|--------|-------|
| Feature A | module | v0.x.x |
| Feature B | module | v0.x.x |

### Key Concepts

| Term | Definition |
|------|-----------|
| Concept A | Explanation |
| Concept B | Explanation |

---

## 2. Quick Start

### Prerequisites

- Java 21, Maven 3.9+
- Docker (for Jaeger / PostgreSQL)

### Step-by-Step

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Build and run
mvn clean install -DskipTests
mvn -pl order-infrastructure spring-boot:run
```

### Verify It Works

```bash
# Expected result
curl http://localhost:8080/actuator/prometheus | grep <new-metric-name>
```

---

## 3. Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `config.property` | boolean | `true` | Enable/disable feature |
| `config.property` | string | `"value"` | Description |

### Example Configuration

```yaml
config:
  property: value
```

---

## 4. Public API / Metrics Reference

### New Metrics (Prometheus)

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `metric.name` | Timer | `tag1`, `tag2` | Description |
| `metric.name` | Counter | `tag1` | Description |

### API Endpoints (If Applicable)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/...` | Description |

### Request / Response Format

```json
{
  "field": "value"
}
```

### Span / Trace Attributes

| Span Name | Attribute | Description |
|-----------|-----------|-------------|
| `inventory.occupy` | `sku` | SKU being reserved (Jaeger only) |

### Log Fields (ECS Format)

| Field Path | Type | Example | Description |
|-----------|------|---------|-------------|
| `event.action` | string | `"saga.step.end"` | Step lifecycle event |
| `event.duration` | long (nanos) | `340000000` | Duration in nanoseconds |
| `saga.step` | string | `"WMS_PHASE"` | Saga step identifier |

---

## 5. Use Cases

### Use Case 1: Debugging Slow Orders

**Scenario:** An order took 5 seconds to complete. Which step was slow?

**Steps:**
1. Query Prometheus for step p99: `histogram_quantile(0.99, saga_step_duration_seconds{step="WMS_PHASE"})`
2. If WMS is slow, check `o11y.client.requests` for the WMS host
3. Check gap duration: `saga_gap_duration_seconds{gap="WMS_ACKED_TO_PICKED"}`

### Use Case 2: ...

---

## 6. Examples

### Prometheus Query Examples

```promql
# Step-level p95 latency
histogram_quantile(0.95, rate(saga_step_duration_seconds_bucket[5m]))

# Total orders by status
sum by (status) (orders_placed_total)

# Inventory reservation success rate
sum by (result) (inventory_reservation_total)
```

### Kibana / Elasticsearch Query

```json
// Find all saga step events for a specific order
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
```

### Jaeger Query

```
{resource.service.name="order-service"} && {sku="SKU-1"}
```

### Curl Examples

```bash
# Place an order and check step timing
curl -s http://localhost:8080/actuator/prometheus | grep saga_step
```

---

## 7. Known Limitations

| Limitation | Workaround | Planned Fix |
|-----------|-----------|-------------|
| Issue A | Workaround | Sprint N+1 |
| Issue B | Workaround | Future |