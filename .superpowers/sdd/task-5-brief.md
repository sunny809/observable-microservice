### Task 5: 技术 Infrastructure 看板 JSON

**Files:**
- Create: `docker/grafana/dashboards/technical-dashboard.json`

**Panels:**
1. JVM 行: Heap 使用率、GC 暂停、线程状态
2. 数据库: HikariCP 连接池、慢查询
3. HTTP + Resilience4j: 延迟 P50/P95/P99、熔断器状态、重试/限流

- [ ] **Step 1: 创建技术看板 JSON**

```json
{
  "title": "Order Service — Technical Dashboard",
  "uid": "order-service-technical",
  "schemaVersion": 39,
  "version": 1,
  "timezone": "browser",
  "editable": true,
  "refresh": "30s",
  "time": {
    "from": "now-1h",
    "to": "now"
  },
  "timepicker": {},
  "templating": {
    "list": []
  },
  "annotations": {
    "list": []
  },
  "panels": [
    {
      "id": 1,
      "title": "Heap Usage",
      "type": "gauge",
      "gridPos": {"h": 6, "w": 4, "x": 0, "y": 0},
      "targets": [
        {
          "expr": "sum(jvm_memory_used_bytes{area=\"heap\"}) / sum(jvm_memory_max_bytes{area=\"heap\"}) * 100",
          "legendFormat": "Heap %",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "percent",
          "color": {"mode": "thresholds"},
          "thresholds": {
            "steps": [
              {"color": "green", "value": null},
              {"color": "orange", "value": 70},
              {"color": "red", "value": 90}
            ]
          },
          "min": 0,
          "max": 100
        }
      }
    },
    {
      "id": 2,
      "title": "GC Pause Time",
      "type": "timeseries",
      "gridPos": {"h": 6, "w": 4, "x": 4, "y": 0},
      "targets": [
        {
          "expr": "rate(jvm_gc_pause_seconds_sum[1m])",
          "legendFormat": "{{action}}",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "s",
          "color": {"mode": "palette-classic"}
        }
      }
    },
    {
      "id": 3,
      "title": "Thread States",
      "type": "timeseries",
      "gridPos": {"h": 6, "w": 4, "x": 8, "y": 0},
      "targets": [
        {
          "expr": "jvm_threads_state",
          "legendFormat": "{{state}}",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "short",
          "color": {"mode": "palette-classic"}
        }
      }
    },
    {
      "id": 4,
      "title": "HikariCP Connection Pool",
      "type": "timeseries",
      "gridPos": {"h": 6, "w": 6, "x": 0, "y": 6},
      "targets": [
        {
          "expr": "hikaricp_connections_active",
          "legendFormat": "Active",
          "refId": "A"
        },
        {
          "expr": "hikaricp_connections_idle",
          "legendFormat": "Idle",
          "refId": "B"
        },
        {
          "expr": "hikaricp_connections_pending",
          "legendFormat": "Pending",
          "refId": "C"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "short",
          "color": {"mode": "palette-classic"}
        }
      }
    },
    {
      "id": 5,
      "title": "HTTP Request Latency P50 / P95 / P99",
      "type": "timeseries",
      "gridPos": {"h": 6, "w": 6, "x": 6, "y": 6},
      "targets": [
        {
          "expr": "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))",
          "legendFormat": "P50",
          "refId": "A"
        },
        {
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))",
          "legendFormat": "P95",
          "refId": "B"
        },
        {
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))",
          "legendFormat": "P99",
          "refId": "C"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "s",
          "color": {"mode": "palette-classic"}
        }
      }
    },
    {
      "id": 6,
      "title": "Circuit Breaker State",
      "type": "stat",
      "gridPos": {"h": 4, "w": 4, "x": 0, "y": 12},
      "targets": [
        {
          "expr": "resilience4j_circuitbreaker_state",
          "legendFormat": "{{name}}",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "color": {"mode": "thresholds"},
          "thresholds": {
            "steps": [
              {"color": "green", "value": null},
              {"color": "orange", "value": 0.5},
              {"color": "red", "value": 1.5}
            ]
          }
        }
      }
    },
    {
      "id": 7,
      "title": "Retry / Rate Limiter Calls",
      "type": "timeseries",
      "gridPos": {"h": 4, "w": 4, "x": 4, "y": 12},
      "targets": [
        {
          "expr": "rate(resilience4j_retry_calls_total[1m])",
          "legendFormat": "Retry: {{name}}",
          "refId": "A"
        },
        {
          "expr": "rate(resilience4j_ratelimiter_calls_total[1m])",
          "legendFormat": "RateLimit: {{name}}",
          "refId": "B"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "cps",
          "color": {"mode": "palette-classic"}
        }
      }
    }
  ]
}
```

- [ ] **Step 2: 验证 JSON 格式**

Run: `cat docker/grafana/dashboards/technical-dashboard.json | python3 -m json.tool > /dev/null && echo "VALID JSON"`

Expected: `VALID JSON`

- [ ] **Step 3: Commit**

```bash
git add docker/grafana/dashboards/technical-dashboard.json
git commit -m "feat(observability): add technical dashboard JSON"
```

---

