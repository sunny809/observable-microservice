# 可观测性深化：Grafana 仪表盘 + Loki 日志聚合

## 概述

在已有 Micrometer 业务指标 + OTel 链路追踪 + 结构化日志的基础上，搭建完整的可观测性可视化栈：Grafana 仪表盘（业务 + 技术）、Loki 日志聚合（Promtail → Loki → Grafana），全部通过 docker-compose 预置配置一键启动。

## 目标

1. **业务运营看板** — 订单量、成功率、Saga 耗时、失败分布等业务指标可视化
2. **技术 Infrastructure 看板** — JVM、DB 连接池、HTTP 延迟、熔断器等运维指标
3. **Loki 日志聚合** — 结构化日志采集、搜索、与指标关联

## 架构

```
order-demo (8080)          Prometheus (9090)         Grafana (3000)
  /actuator/prometheus ──▶  scrape 15s ────────────▶  DS: Prometheus
  JSON stdout ──────────▶  Promtail (9080) ────────▶  DS: Loki
                              └── ship logs ──────▶  Loki (3100)
```

所有组件添加到现有 docker-compose.yml，不入侵应用代码。

## 组件配置

### 1. Prometheus

`docker/prometheus/prometheus.yml`:
- scrape `order-demo:8080/actuator/prometheus`，15s 间隔
- 自监控 `prometheus:9090`

### 2. Grafana

Provisioning 自动配置:
- `docker/grafana/datasources/datasources.yml` — 预配 Prometheus + Loki 数据源
- `docker/grafana/dashboards/dashboard.yml` — 自动加载 JSON 模型
- `docker/grafana/dashboards/business-dashboard.json` — 业务运营看板
- `docker/grafana/dashboards/technical-dashboard.json` — 技术 Infrastructure 看板

### 3. Loki

`docker/loki/loki-config.yml`:
- 单实例本地存储模式
- 保留 7 天
- 接收 Promtail 推送

### 4. Promtail

`docker/loki/promtail-config.yml`:
- 读取 Docker 容器日志（`/var/log/containers/*.log`）
- 提取 service、level 作为 Loki labels
- pipeline 阶段: json → timestamp → labels → output

## 仪表盘设计

### 业务运营看板

| 区域 | 面板 | 指标 |
|------|------|------|
| KPI 行 | 总订单数 | `orders_placed_total` |
| | 成功率 | `1 - rate(orders_failed_total[1m]) / rate(orders_placed_total[1m])` |
| | Saga P99 | `histogram_quantile(0.99, saga_duration_seconds_bucket)` |
| | 失败率 | `rate(orders_failed_total[1m])` |
| 时序 | 订单放置速率 | `rate(orders_placed_total[1m])` |
| | 失败分布 | `rate(orders_failed_total[1m]) by (reason)` |
| | Saga 步骤耗时 | `saga_step_duration_seconds_sum` by (step) |
| 详情 | 库存预留 | `rate(inventory_reservation_total[1m]) by (status)` |
| | Saga Gap | `saga_gap_duration_seconds` |

### 技术 Infrastructure 看板

| 区域 | 面板 | 指标 |
|------|------|------|
| JVM | Heap 使用率 | `jvm_memory_used_bytes{area="heap"}` / `jvm_memory_max_bytes{area="heap"}` |
| | GC 暂停 | `rate(jvm_gc_pause_seconds_sum[1m])` |
| | 线程状态 | `jvm_threads_state` |
| 数据库 | HikariCP 池 | `hikaricp_connections_active / idle / pending` |
| HTTP | 延迟 P50/P95/P99 | `http_server_requests_seconds` |
| Resilience4j | 熔断器状态 | `resilience4j_circuitbreaker_state` |
| | 重试/限流 | `resilience4j_retry_calls / ratelimiter_calls` |

## 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `docker-compose.yml` | 修改 | 添加 prometheus / grafana / loki / promtail 服务 |
| `docker/prometheus/prometheus.yml` | 新增 | Prometheus scrape 配置 |
| `docker/grafana/datasources/datasources.yml` | 新增 | 预配数据源 |
| `docker/grafana/dashboards/dashboard.yml` | 新增 | 自动加载配置 |
| `docker/grafana/dashboards/business-dashboard.json` | 新增 | 业务看板 JSON |
| `docker/grafana/dashboards/technical-dashboard.json` | 新增 | 技术看板 JSON |
| `docker/loki/loki-config.yml` | 新增 | Loki 配置 |
| `docker/loki/promtail-config.yml` | 新增 | Promtail 配置 |

## 测试

- 启动验证: `docker compose up -d` 后各组件正常启动
- 指标验证: Grafana Explore 中查询 Prometheus 指标
- 日志验证: Grafana Explore 中查询 Loki 日志流
- 看板验证: 预置看板加载无误，数据正常渲染