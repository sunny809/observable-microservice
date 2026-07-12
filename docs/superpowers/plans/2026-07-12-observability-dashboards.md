# 可观测性深化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 order-demo 搭建完整的可观测性可视化栈：Grafana 仪表盘（业务运营 + 技术 Infrastructure）+ Loki 日志聚合（Promtail → Loki → Grafana），全部通过 docker-compose 预置配置一键启动。

**Architecture:** 不修改应用代码，仅通过 docker-compose 扩展添加 Prometheus/Grafana/Loki/Promtail 容器。当前已有的 Micrometer 指标（/actuator/prometheus）和 Logstash JSON 结构化日志直接作为数据源。Grafana 使用 Provisioning 机制自动加载预制的 JSON 仪表盘和数据源配置。

**Tech Stack:** Prometheus 2.45+, Grafana 10+, Loki 2.9+, Promtail 2.9+, Docker Compose

**Key Metrics Already Available:**
- Business: `orders_placed_total`, `orders_failed_total`, `inventory_reservation_total`, `saga_duration_seconds`, `saga_step_duration_seconds`, `saga_gap_duration_seconds`
- Technical: `jvm_*`, `hikaricp_*`, `http_server_requests_seconds`, `resilience4j_*`

## Global Constraints

- 不修改应用代码（order-demo 容器不变）
- Grafana 仪表盘使用 Provisioning 机制（JSON 文件），不通过 UI 手动创建
- 所有配置以文件形式版本化管理
- 端口避免冲突：Prometheus 9090, Grafana 3000, Loki 3100, Promtail 9080
- 使用 Grafana 9.x+ 兼容的 dashboard JSON schemaVersion 39

---

### Task 1: Prometheus 抓取配置

**Files:**
- Create: `docker/prometheus/prometheus.yml`
- Test: `docker compose config` 验证

**Interfaces:**
- Produces: Prometheus 配置文件，定义 scrape targets

- [ ] **Step 1: 创建 Prometheus 配置**

```yaml
# docker/prometheus/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'order-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['order-demo:8080']
        labels:
          application: 'order-service'

  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

- [ ] **Step 2: 验证配置**

Run: `docker run --rm -v $(pwd)/docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus:latest --config.file=/etc/prometheus/prometheus.yml --dry-run 2>&1 | head -20`

Expected: 无错误输出，配置加载成功

- [ ] **Step 3: Commit**

```bash
git add docker/prometheus/prometheus.yml
git commit -m "feat(observability): add Prometheus scrape config"
```

---

### Task 2: Loki + Promtail 配置

**Files:**
- Create: `docker/loki/loki-config.yml`
- Create: `docker/loki/promtail-config.yml`

**Interfaces:**
- Produces: Loki 本地存储配置 + Promtail 日志采集 pipeline

- [ ] **Step 1: 创建 Loki 配置**

```yaml
# docker/loki/loki-config.yml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

limits_config:
  retention_period: 168h  # 7 days
  max_query_lookback: 168h
```

- [ ] **Step 2: 创建 Promtail 配置**

```yaml
# docker/loki/promtail-config.yml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 5s
    relabel_configs:
      - source_labels: ['__meta_docker_container_name']
        regex: '/(.*)'
        target_label: 'container'
      - source_labels: ['__meta_docker_container_log_stream']
        target_label: 'log_stream'
    pipeline_stages:
      - json:
          expressions:
            level: level
            traceId: traceId
            service: service
            orderId: orderId
      - labels:
          level:
          service:
      - timestamp:
          source: '@timestamp'
          format: RFC3339
```

- [ ] **Step 3: Commit**

```bash
git add docker/loki/loki-config.yml docker/loki/promtail-config.yml
git commit -m "feat(observability): add Loki + Promtail config"
```

---

### Task 3: Grafana 数据源 Provisioning

**Files:**
- Create: `docker/grafana/datasources/datasources.yml`

**Interfaces:**
- Produces: Grafana 自动配置 Prometheus + Loki 数据源

- [ ] **Step 1: 创建 Grafana datasources 配置**

```yaml
# docker/grafana/datasources/datasources.yml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    isDefault: false
    editable: false
    jsonData:
      maxLines: 1000
```

- [ ] **Step 2: 创建 Grafana dashboard provisioning 配置**

```yaml
# docker/grafana/dashboards/dashboard.yml
apiVersion: 1

providers:
  - name: 'Order Service Dashboards'
    orgId: 1
    folder: 'Order Service'
    type: file
    disableDeletion: false
    editable: true
    options:
      path: /etc/grafana/provisioning/dashboards
```

- [ ] **Step 3: Commit**

```bash
git add docker/grafana/datasources/datasources.yml docker/grafana/dashboards/dashboard.yml
git commit -m "feat(observability): add Grafana provisioning configs"
```

---

### Task 4: 业务运营看板 JSON

**Files:**
- Create: `docker/grafana/dashboards/business-dashboard.json`

**Panels:**
1. KPI 行: 总订单数、成功率、Saga P99、失败率 (Stat 面板)
2. 时序: 订单放置速率、失败分布、Saga 步骤耗时
3. 详情: 库存预留、Saga Gap

- [ ] **Step 1: 创建业务看板 JSON**

```json
{
  "title": "Order Service — Business Dashboard",
  "uid": "order-service-business",
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
      "title": "Total Orders",
      "type": "stat",
      "gridPos": {"h": 4, "w": 3, "x": 0, "y": 0},
      "targets": [
        {
          "expr": "orders_placed_total",
          "legendFormat": "Total",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "short",
          "color": {"mode": "fixed"},
          "thresholds": {"steps": [{"color": "green", "value": null}]}
        }
      }
    },
    {
      "id": 2,
      "title": "Success Rate",
      "type": "stat",
      "gridPos": {"h": 4, "w": 3, "x": 3, "y": 0},
      "targets": [
        {
          "expr": "1 - rate(orders_failed_total[1h]) / rate(orders_placed_total[1h])",
          "legendFormat": "Success Rate",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "percentunit",
          "color": {"mode": "thresholds"},
          "thresholds": {
            "steps": [
              {"color": "red", "value": null},
              {"color": "orange", "value": 0.95},
              {"color": "green", "value": 0.99}
            ]
          }
        }
      }
    },
    {
      "id": 3,
      "title": "Saga P99 Duration",
      "type": "stat",
      "gridPos": {"h": 4, "w": 3, "x": 6, "y": 0},
      "targets": [
        {
          "expr": "histogram_quantile(0.99, sum(rate(saga_duration_seconds_bucket[1h])) by (le))",
          "legendFormat": "P99",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "s",
          "color": {"mode": "thresholds"},
          "thresholds": {
            "steps": [
              {"color": "green", "value": null},
              {"color": "orange", "value": 3},
              {"color": "red", "value": 5}
            ]
          }
        }
      }
    },
    {
      "id": 4,
      "title": "Failure Rate",
      "type": "stat",
      "gridPos": {"h": 4, "w": 3, "x": 9, "y": 0},
      "targets": [
        {
          "expr": "rate(orders_failed_total[1h])",
          "legendFormat": "Failures",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "cps",
          "color": {"mode": "thresholds"},
          "thresholds": {
            "steps": [
              {"color": "green", "value": null},
              {"color": "orange", "value": 0.01},
              {"color": "red", "value": 0.05}
            ]
          }
        }
      }
    },
    {
      "id": 5,
      "title": "Order Placement Rate",
      "type": "timeseries",
      "gridPos": {"h": 8, "w": 6, "x": 0, "y": 4},
      "targets": [
        {
          "expr": "rate(orders_placed_total[1m])",
          "legendFormat": "orders/s",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "cps",
          "color": {"mode": "palette-classic"}
        }
      }
    },
    {
      "id": 6,
      "title": "Failure Distribution",
      "type": "timeseries",
      "gridPos": {"h": 8, "w": 6, "x": 6, "y": 4},
      "targets": [
        {
          "expr": "rate(orders_failed_total[1m])",
          "legendFormat": "{{reason}}",
          "refId": "A"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "cps",
          "color": {"mode": "palette-classic"}
        }
      }
    },
    {
      "id": 7,
      "title": "Saga Step Duration",
      "type": "timeseries",
      "gridPos": {"h": 8, "w": 6, "x": 0, "y": 12},
      "targets": [
        {
          "expr": "rate(saga_step_duration_seconds_sum[1m])",
          "legendFormat": "{{step}}",
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
      "id": 8,
      "title": "Gap Duration (POST_COMMIT_TO_WMS)",
      "type": "timeseries",
      "gridPos": {"h": 8, "w": 6, "x": 6, "y": 12},
      "targets": [
        {
          "expr": "saga_gap_duration_seconds",
          "legendFormat": "gap",
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
      "id": 9,
      "title": "Inventory Reservation Rate",
      "type": "timeseries",
      "gridPos": {"h": 8, "w": 6, "x": 0, "y": 20},
      "targets": [
        {
          "expr": "rate(inventory_reservation_total[1m])",
          "legendFormat": "{{status}}",
          "refId": "A"
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

Run: `cat docker/grafana/dashboards/business-dashboard.json | python3 -m json.tool > /dev/null && echo "VALID JSON"`

Expected: `VALID JSON`

- [ ] **Step 3: Commit**

```bash
git add docker/grafana/dashboards/business-dashboard.json
git commit -m "feat(observability): add business dashboard JSON"
```

---

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

### Task 6: 更新 docker-compose.yml

**Files:**
- Modify: `docker-compose.yml` (添加 prometheus / grafana / loki / promtail 服务)

**Interfaces:**
- Consumes: 所有 Task 1-5 创建的配置文件
- Produces: 完整的 docker-compose.yml，`docker compose up -d` 一键启动全部服务

- [ ] **Step 1: 添加 Prometheus 服务**

在 `docker-compose.yml` 的 `services:` 块末尾（`tms-mock:` 之后）、`volumes:` 之前添加：

```yaml
  prometheus:
    image: prom/prometheus:v2.45.6
    container_name: order-service-prometheus
    ports:
      - '9090:9090'
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    restart: unless-stopped
```

- [ ] **Step 2: 添加 Loki 服务**

```yaml
  loki:
    image: grafana/loki:2.9.8
    container_name: order-service-loki
    ports:
      - '3100:3100'
    volumes:
      - ./docker/loki/loki-config.yml:/etc/loki/loki-config.yml
    command:
      - '-config.file=/etc/loki/loki-config.yml'
    restart: unless-stopped
```

- [ ] **Step 3: 添加 Promtail 服务**

```yaml
  promtail:
    image: grafana/promtail:2.9.8
    container_name: order-service-promtail
    volumes:
      - ./docker/loki/promtail-config.yml:/etc/promtail/promtail-config.yml
      - /var/run/docker.sock:/var/run/docker.sock
    command:
      - '-config.file=/etc/promtail/promtail-config.yml'
    restart: unless-stopped
    depends_on:
      - loki
```

- [ ] **Step 4: 添加 Grafana 服务**

```yaml
  grafana:
    image: grafana/grafana:10.4.13
    container_name: order-service-grafana
    ports:
      - '3000:3000'
    volumes:
      - ./docker/grafana/datasources:/etc/grafana/provisioning/datasources
      - ./docker/grafana/dashboards:/etc/grafana/provisioning/dashboards
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH=/etc/grafana/provisioning/dashboards/business-dashboard.json
    restart: unless-stopped
    depends_on:
      - prometheus
      - loki
```

- [ ] **Step 5: 验证 docker-compose 格式**

Run: `docker compose config 2>&1 | head -30`

Expected: 无错误输出，显示所有服务配置

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml
git commit -m "feat(observability): add Prometheus / Grafana / Loki / Promtail to docker-compose"
```

---

### Task 7: 启动验证 + 文档

**Files:**
- Modify: `README.md` (添加可观测性章节)

- [ ] **Step 1: 启动所有服务**

Run: `docker compose up -d 2>&1`

Expected: 所有容器正常启动

- [ ] **Step 2: 验证 Prometheus 指标采集**

Run: `curl -s http://localhost:9090/api/v1/targets | python3 -c "import sys,json; d=json.load(sys.stdin); [print(f'{t[\"labels\"][\"job\"]}: {t[\"health\"]}') for t in d['data']['activeTargets']]"`

Expected:
```
order-service: UP
prometheus: UP
```

- [ ] **Step 3: 验证 Prometheus 能查到业务指标**

Run: `curl -s 'http://localhost:9090/api/v1/query?query=orders_placed_total' | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'orders_placed_total: {len(d[\"data\"][\"result\"])} results')"`

Expected: `orders_placed_total: 1+ results`

- [ ] **Step 4: 验证 Grafana 数据源已配置**

Run: `curl -s http://admin:admin@localhost:3000/api/datasources | python3 -c "import sys,json; [print(f'{d[\"name\"]}: {d[\"type\"]}') for d in json.load(sys.stdin)]"`

Expected:
```
Prometheus: prometheus
Loki: loki
```

- [ ] **Step 5: 验证 Grafana 仪表盘已加载**

Run: `curl -s http://admin:admin@localhost:3000/api/search | python3 -c "import sys,json; [print(d['title']) for d in json.load(sys.stdin)]"`

Expected:
```
Order Service — Business Dashboard
Order Service — Technical Dashboard
```

- [ ] **Step 6: 验证 Loki 日志已接收**

Run: `curl -s 'http://localhost:3100/loki/api/v1/labels' | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'Loki labels: {d[\"data\"]}')"`

Expected: `Loki labels: ['__name__', 'container', 'job', 'level', 'service', ...]`

- [ ] **Step 7: 更新 README.md**

在 `README.md` 的可观测性章节添加：

```markdown
### 可观测性栈

项目提供完整的本地可观测性栈，通过 docker-compose 一键启动：

```bash
docker compose up -d
```

| 组件 | 地址 | 说明 |
|------|------|------|
| **Grafana** | http://localhost:3000 | 仪表盘 (admin/admin) |
| **Prometheus** | http://localhost:9090 | 指标存储 |
| **Loki** | http://localhost:3100 | 日志聚合 |
| **Jaeger** | http://localhost:16686 | 链路追踪 |

#### 预置仪表盘

- **Business Dashboard** — 订单量、成功率、Saga 耗时、失败分布、库存预留
- **Technical Dashboard** — JVM、DB 连接池、HTTP 延迟、熔断器状态

（注：pre-existing 的 Grafana 和 Prometheus 等组件已配置，但业务指标需要先发送订单请求才能看到数据）
```

- [ ] **Step 8: Commit**

```bash
git add README.md
git commit -m "docs: add observability stack section to README"
```