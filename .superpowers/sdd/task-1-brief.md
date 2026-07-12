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

