# Observability Stack Verification Guide

This guide walks through verifying that the local observability stack (Grafana, Prometheus, Loki, Jaeger) is running correctly after starting it with `docker compose up -d`.

## Prerequisites

- Docker and docker-compose installed
- All services started: `docker compose up -d`
- The application is running and accepting requests

## Step 1: Verify All Containers Are Running

```bash
docker compose ps
```

Expected: all services show `Up` status.

## Step 2: Verify Prometheus Target Collection

Check that Prometheus is scraping the configured targets:

```bash
curl -s http://localhost:9090/api/v1/targets | python3 -c "
import sys, json
d = json.load(sys.stdin)
for t in d['data']['activeTargets']:
    print(f\"{t['labels']['job']}: {t['health']}\")
"
```

Expected output:
```
order-service: UP
prometheus: UP
```

## Step 3: Verify Business Metrics Reach Prometheus

Query Prometheus for the custom business metric:

```bash
curl -s 'http://localhost:9090/api/v1/query?query=orders_placed_total' | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'orders_placed_total: {len(d[\"data\"][\"result\"])} results')
"
```

Expected: `orders_placed_total: 1+ results` (at least one result after sending an order request).

## Step 4: Verify Grafana Data Sources

Check that Grafana has Prometheus and Loki configured as data sources:

```bash
curl -s http://admin:admin@localhost:3000/api/datasources | python3 -c "
import sys, json
for d in json.load(sys.stdin):
    print(f\"{d['name']}: {d['type']}\")
"
```

Expected output:
```
Prometheus: prometheus
Loki: loki
```

## Step 5: Verify Grafana Dashboards Are Loaded

List the pre-configured dashboards in Grafana:

```bash
curl -s http://admin:admin@localhost:3000/api/search | python3 -c "
import sys, json
for d in json.load(sys.stdin):
    print(d['title'])
"
```

Expected output:
```
Order Service — Business Dashboard
Order Service — Technical Dashboard
```

## Step 6: Verify Loki Log Reception

Check that Loki is receiving logs and has labels available:

```bash
curl -s 'http://localhost:3100/loki/api/v1/labels' | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'Loki labels: {d[\"data\"]}')
"
```

Expected: `Loki labels: ['__name__', 'container', 'job', 'level', 'service', ...]` (labels may vary, but at minimum `container`, `job`, `level`, `service` should be present).

## End-to-End Smoke Test

Send a sample order request to populate business metrics, then verify them:

```bash
# 1. Place an order
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-1",
    "idempotencyKey": "verify-001",
    "items": [{"sku": "SKU-1", "quantity": 2}]
  }'

# 2. Check the business metric in Prometheus
curl -s 'http://localhost:9090/api/v1/query?query=orders_placed_total' | python3 -c "
import sys, json
d = json.load(sys.stdin)
if d['data']['result']:
    print('PASS: orders_placed_total metric found')
else:
    print('FAIL: orders_placed_total metric not found')
"

# 3. Open Grafana dashboard
echo "Open http://localhost:3000 in your browser (admin/admin)"
```

## Troubleshooting

| Symptom | Likely Cause | Check |
|---------|-------------|-------|
| Prometheus targets show `DOWN` | Application not running | `docker compose logs order-service` |
| Grafana returns 401 | Wrong credentials | Verify `admin/admin` or check Grafana config |
| Loki returns empty labels | No logs collected yet | Send a request to the application |
| Dashboard not found | Provisioning not complete | Check `docker compose logs grafana` |
