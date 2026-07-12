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

