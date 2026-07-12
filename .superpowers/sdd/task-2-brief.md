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

