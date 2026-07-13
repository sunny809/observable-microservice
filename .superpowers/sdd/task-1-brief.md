### Task 1: 扩展 saga_logs 表 + 新建 compensation_logs 表

**Files:**
- Create: `order-infrastructure/src/main/resources/db/migration/V4__extend_saga_logs.sql`
- Create: `order-infrastructure/src/main/resources/db/migration/V5__create_compensation_logs.sql`

**Interfaces:**
- Produces: 扩展后的 saga_logs 表结构 + 新的 compensation_logs 表

- [ ] **Step 1: 创建 V4 迁移脚本**

```sql
-- V4__extend_saga_logs.sql
ALTER TABLE saga_logs
    ADD COLUMN saga_type VARCHAR(50) NOT NULL DEFAULT 'ORDER_PLACEMENT',
    ADD COLUMN step_name VARCHAR(50),
    ADD COLUMN step_status VARCHAR(20),
    ADD COLUMN started_at TIMESTAMP,
    ADD COLUMN completed_at TIMESTAMP,
    ADD COLUMN compensation_status VARCHAR(20),
    ADD COLUMN retry_count INT DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP;

CREATE INDEX idx_saga_logs_status_started_at ON saga_logs(step_status, started_at);
CREATE INDEX idx_saga_logs_order_id ON saga_logs(order_id);
```

- [ ] **Step 2: 创建 V5 迁移脚本**

```sql
-- V5__create_compensation_logs.sql
CREATE TABLE compensation_logs (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    step_name VARCHAR(50) NOT NULL,
    reservation_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_compensation_logs_order_id ON compensation_logs(order_id);
```

- [ ] **Step 3: 验证迁移脚本**

Run: `mvn flyway:validate -pl order-infrastructure`
Expected: 无错误

- [ ] **Step 4: Commit**

```bash
git add order-infrastructure/src/main/resources/db/migration/
git commit -m "feat(saga): add saga_logs extension and compensation_logs migration"
```

---

