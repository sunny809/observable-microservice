# 数据密集型应用优化设计文档

> 为 order-demo 补齐数据密集型应用关键设计模式：数据生命周期管理（冷热分离 + 归档）、JSON 原生存储 + 查询能力、数据版本快照 + 时间点查询。

**目标：** 完善数据密集型混合架构（事务完整性 + 高吞吐），解决数据无限增长、JSON blob 查询能力缺失、状态时间点查询缺失三大痛点。

**架构：** 在现有六边形架构内扩展，不改变模块边界。归档策略通过 @Scheduled 定时任务实现；JSON 原生存储通过 Flyway migration + JPA AttributeConverter 替代手动序列化；快照通过新增表 + 事件监听触发。

**技术栈：** Spring Boot 3.4.3, JPA AttributeConverter, PostgreSQL JSONB / H2 JSON, @Scheduled, Flyway

---

## 1. 数据生命周期管理（冷热分离 + 归档）

### 1.1 问题

`saga_logs`、`outbox_events`、`compensation_logs` 无限增长。生产环境 1 个月后这些表可能百万级：
- `saga_logs`：每个订单产生 4-8 条记录，日订单 10K → 日增 40-80K 行
- `outbox_events`：每个订单 1-2 条事件，日增 10-20K 行
- `compensation_logs`：补偿场景较少，但无清理机制

### 1.2 归档策略

| 表 | 归档条件 | 操作 | 保留策略 |
|------|---------|------|---------|
| `saga_logs` | `step_status` IN ('COMPLETED','FAILED','COMPENSATED') 且 `completed_at` < 30 天前 | 迁移到 `saga_logs_archive` | 归档表保留 1 年 |
| `outbox_events` | `status` = 'SENT' 且 `sent_at` < 7 天前 | 直接删除 | 不保留 |
| `compensation_logs` | `status` = 'COMPLETED' 且 `attempted_at` < 30 天前 | 直接删除 | 不保留 |

### 1.3 数据模型

```sql
-- V10 migration: saga_logs_archive 表
CREATE TABLE saga_logs_archive (
    id BIGINT PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    step VARCHAR(255) NOT NULL,
    detail TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    saga_type VARCHAR(50),
    step_name VARCHAR(50),
    step_status VARCHAR(20),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    compensation_status VARCHAR(20),
    retry_count INT,
    next_retry_at TIMESTAMP,
    previous_status VARCHAR(32),
    new_status VARCHAR(32),
    changed_by VARCHAR(100)
);

CREATE INDEX idx_saga_logs_archive_order_id ON saga_logs_archive(order_id);
```

### 1.4 实现

```java
@Component
public class DataLifecycleManager {
    private final SagaLogPort sagaLogPort;
    private final OutboxEventJpaRepository outboxRepo;
    private final CompensationLogJpaRepository compensationLogRepo;
    private final MetricsPort metricsPort;

    @Scheduled(cron = "${app.lifecycle.archive-cron:0 0 3 * * ?}")
    @Transactional
    public void archiveAndCleanup() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        int archived = sagaLogPort.archiveCompletedOlderThan(thirtyDaysAgo);
        int outboxDeleted = outboxRepo.deleteByStatusAndSentAtBefore("SENT", sevenDaysAgo);
        int compensationDeleted = compensationLogRepo.deleteByStatusAndAttemptedAtBefore("COMPLETED", thirtyDaysAgo);

        metricsPort.recordLifecycleArchived(archived);
        metricsPort.recordLifecycleDeleted(outboxDeleted + compensationDeleted);
    }
}
```

### 1.5 架构适配

| 组件 | 变更 |
|------|------|
| `SagaLogPort` | 新增 `int archiveCompletedOlderThan(LocalDateTime threshold)` |
| `SagaLogPersistenceAdapter` | 实现归档逻辑：INSERT INTO saga_logs_archive SELECT ... WHERE ... |
| `SagaLogJpaRepository` | 新增 `@Modifying` 归档查询 + 删除已归档记录 |
| `OutboxEventJpaRepository` | 新增 `deleteByStatusAndSentAtBefore()` |
| `CompensationLogJpaRepository` | 新增 `deleteByStatusAndAttemptedAtBefore()` |
| `MetricsPort` | 新增 `recordLifecycleArchived(int)` + `recordLifecycleDeleted(int)` |
| `OrderMetrics` | 实现归档指标 |
| `application.yml` | 新增 `app.lifecycle.archive-cron` 配置 |

---

## 2. JSON 原生存储 + 查询能力

### 2.1 问题

当前 `orders.items` 和 `orders.reservation_ids` 是 `TEXT` 类型，存 JSON 字符串。痛点：
1. 无法 SQL 查询："找出所有包含 SKU-1 的订单" → 只能全表扫描后在 Java 层过滤
2. `OrderPersistenceAdapter` 里有 6 个手动序列化/反序列化方法，代码脆弱
3. 数据完整性无保障——`items` 列可以是任何 TEXT，不保证是合法 JSON

### 2.2 方案

**2.2.1 数据库层**：`TEXT` → `JSONB`（PostgreSQL）/ `JSON`（H2）

```sql
-- V11 migration (PostgreSQL)
ALTER TABLE orders ALTER COLUMN items SET DATA TYPE JSONB USING items::JSONB;
ALTER TABLE orders ALTER COLUMN reservation_ids SET DATA TYPE JSONB USING reservation_ids::JSONB;
```

H2 兼容：H2 2.2+ 支持 `JSON` 类型，但 `ALTER COLUMN SET DATA TYPE` 语法与 PostgreSQL 不同。实现时使用 Flyway 的 `db/migration` 目录分层（`db/migration/common/` + `db/migration/postgresql/`）或条件化 SQL 脚本。H2 测试环境可直接使用 `TEXT` 列 + `@Convert`（JPA AttributeConverter 不依赖数据库 JSON 类型），无需 H2 原生 JSON 支持。

**2.2.2 JPA 层**：`@Convert` + 自定义 `JsonConverter` 替代手动 ObjectMapper

```java
// OrderEntity 改造
@Convert(converter = OrderItemListConverter.class)
@Column(name = "items", columnDefinition = "JSONB")
private List<OrderItem> items;

@Convert(converter = StringListConverter.class)
@Column(name = "reservation_ids", columnDefinition = "JSONB")
private List<String> reservationIds;
```

**2.2.3 查询能力**

```java
// OrderJpaRepository 新增原生 JSON 查询
@Query(value = "SELECT * FROM orders WHERE items @> :skuFilter", nativeQuery = true)
List<OrderEntity> findByItemsContainingSku(@Param("skuFilter") String skuFilter);
```

### 2.3 架构适配

| 组件 | 变更 |
|------|------|
| `OrderEntity` | `String items` → `List<OrderItem> items` + `@Convert`；`String reservationIds` → `List<String> reservationIds` + `@Convert` |
| `OrderPersistenceAdapter` | 删除 `serializeItems`, `deserializeItems`, `serializeReservationIds`, `deserializeReservationIds` 及相关异常处理（约 60 行代码） |
| `OrderItemListConverter` | 新增 JPA AttributeConverter |
| `StringListConverter` | 新增 JPA AttributeConverter |
| `OrderRepositoryPort` | 新增 `List<Order> findBySku(String sku)` |
| `OrderJpaRepository` | 新增原生 JSON 查询方法 |
| `OrderController` | 新增 `GET /api/v1/orders/by-sku?sku=SKU-1` 端点 |

### 2.4 效果

- 删除 ~60 行手动序列化代码
- 支持原生 JSON 查询（PostgreSQL `@>` 操作符）
- 数据库层面保证 JSON 合法性

---

## 3. 数据版本快照 + 时间点查询

### 3.1 问题

`saga_logs` 记录了审计信息，但无法直接回答"订单 X 在昨天下午 3 点是什么状态"。需要手动遍历日志推算——对于运维排障和业务分析都不方便。

### 3.2 方案

**3.2.1 快照表**

```sql
-- V12 migration
CREATE TABLE order_snapshots (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    VARCHAR(36) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    snapshot    TEXT NOT NULL,          -- JSON 序列化的完整 Order 状态
    version     BIGINT NOT NULL,
    reason      VARCHAR(100),           -- 触发原因：ORDER_CREATED, WMS_ACKED, etc.
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshots_order_id_created ON order_snapshots(order_id, created_at DESC);
```

**3.2.2 快照触发**

在现有 `@TransactionalEventListener(AFTER_COMMIT)` handler 中，每次状态变更后保存快照：

```java
// OrderPlacementSaga 中每个状态变更后
orderSnapshotPort.saveSnapshot(order, "WMS_ACKED");
```

**3.2.3 时间点查询**

```java
// OrderQueryPort 新增方法
Optional<OrderSummary> findOrderAt(String orderId, Instant pointInTime);

// SQL 实现
SELECT * FROM order_snapshots
WHERE order_id = :orderId AND created_at <= :pointInTime
ORDER BY created_at DESC
LIMIT 1
```

### 3.3 架构适配

| 组件 | 变更 |
|------|------|
| `OrderSnapshotPort` | 新增接口（`saveSnapshot(Order, String reason)`, `findSnapshotAt(orderId, instant)`） |
| `OrderSnapshotEntity` | 新增 JPA 实体 |
| `OrderSnapshotJpaRepository` | 新增 JPA Repository |
| `OrderSnapshotPersistenceAdapter` | 新增适配器实现 |
| `OrderPlacementSaga` | 每个状态变更后调用 `orderSnapshotPort.saveSnapshot()` |
| `OrderQueryPort` | 新增 `findOrderAt(orderId, instant)` |
| `OrderQueryAdapter` | 实现 `findOrderAt()` |
| `OrderController` | 新增 `GET /api/v1/orders/{orderId}/at?time=2026-08-01T15:00:00Z` |

### 3.4 与归档策略的关系

`order_snapshots` 也需要归档。超过 90 天的快照迁移到 `order_snapshots_archive`。归档策略在 Section 1 的 `DataLifecycleManager` 中一并处理。

---

## 4. 实施顺序

| 步骤 | 内容 | 优先级 | 理由 |
|------|------|--------|------|
| 1 | 数据生命周期管理 | 🔴 高 | 解决数据无限增长问题，影响生产稳定性 |
| 2 | JSON 原生存储 + 查询 | 🟡 中 | 删除脆弱代码，增加查询能力 |
| 3 | 数据版本快照 | 🟢 低 | 增加时间点查询能力，但非紧急 |

### 步骤 1 详细任务

| 任务 | 涉及 |
|------|------|
| 1.1 Flyway V10: 创建 saga_logs_archive 表 | order-infrastructure |
| 1.2 SagaLogPort 新增 archiveCompletedOlderThan | order-application |
| 1.3 SagaLogPersistenceAdapter 实现归档逻辑 | order-adapter |
| 1.4 OutboxEventJpaRepository 新增 deleteByStatusAndSentAtBefore | order-adapter |
| 1.5 CompensationLogJpaRepository 新增 deleteByStatusAndAttemptedAtBefore | order-adapter |
| 1.6 DataLifecycleManager + @Scheduled | order-adapter |
| 1.7 MetricsPort 新增归档指标 | order-application + order-adapter |
| 1.8 application.yml 新增归档配置 | order-infrastructure |
| 1.9 测试 | 全部 |

### 步骤 2 详细任务

| 任务 | 涉及 |
|------|------|
| 2.1 Flyway V11: items/reservation_ids TEXT → JSONB | order-infrastructure |
| 2.2 OrderItemListConverter + StringListConverter | order-adapter |
| 2.3 OrderEntity 改用 @Convert + 类型化字段 | order-adapter |
| 2.4 OrderPersistenceAdapter 删除手动序列化代码 | order-adapter |
| 2.5 OrderRepositoryPort 新增 findBySku | order-application |
| 2.6 OrderJpaRepository 新增原生 JSON 查询 | order-adapter |
| 2.7 OrderController 新增 by-sku 端点 | order-adapter |
| 2.8 测试 | 全部 |

### 步骤 3 详细任务

| 任务 | 涉及 |
|------|------|
| 3.1 Flyway V12: 创建 order_snapshots 表 | order-infrastructure |
| 3.2 OrderSnapshotPort 接口 | order-application |
| 3.3 OrderSnapshotEntity + OrderSnapshotJpaRepository | order-adapter |
| 3.4 OrderSnapshotPersistenceAdapter | order-adapter |
| 3.5 OrderPlacementSaga 集成快照保存 | order-application |
| 3.6 OrderQueryPort 扩展 findOrderAt | order-application |
| 3.7 OrderQueryAdapter 实现 findOrderAt | order-adapter |
| 3.8 OrderController 新增 /at 端点 | order-adapter |
| 3.9 DataLifecycleManager 增加快照归档 | order-adapter |
| 3.10 测试 | 全部 |

---

## 5. 不做的事

| 模式 | 理由 |
|------|------|
| **Event Sourcing** | 订单状态机简单（5 个状态），事件溯源的复杂度远超收益。order_snapshots 已提供足够的时间点查询能力。 |
| **CDC (Debezium)** | 当前没有数据仓库/数据湖需求。Outbox Pattern 已提供可靠的事件投递。 |
| **流处理 (Kafka Streams / Flink)** | 当前吞吐量级不需要流式聚合。Micrometer + Prometheus 的实时指标足够。 |
| **CQRS 跨服务** | 当前是单体应用，读写分离在数据库视图层面足够。 |
