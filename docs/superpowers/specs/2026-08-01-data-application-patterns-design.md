# 数据应用设计模式 — 混合路线设计文档

> 为 order-demo 补齐数据应用关键设计模式：Outbox Pattern、乐观锁 + 审计日志、轻量级 CQRS 读模型。

**目标：** 解决当前最严重的数据一致性缺陷（双写不一致），增加数据版本控制和审计能力，为复杂查询预留独立空间，同时避免引入 Event Sourcing 等高复杂度模式。

**架构：** 在现有六边形架构内扩展，不改变模块边界。Outbox 替代直接 Kafka 发送；乐观锁增强写安全；CQRS 读模型通过数据库视图实现读写分离。

**技术栈：** Spring Boot 3.4.3, JPA @Version, Flyway, @Scheduled, KafkaTemplate

---

## 1. Outbox Pattern（事务发件箱）

### 1.1 问题

当前 `placeOrder()` 事务内写 DB + 发布事件，`@TransactionalEventListener(AFTER_COMMIT)` 延迟了外部调用，但 `onWmsRequired` 里的 `wmsPort.sendInstruction()` 调用 Kafka 时不在事务内。

**风险场景**：
1. DB 已提交（订单已创建）
2. Kafka 发送失败（网络超时、broker 不可用）
3. 结果：订单卡在 `CREATED` 状态，只有超时检测器 5 分钟后才能补偿

### 1.2 方案

把"发消息"变成"写数据库"，让消息和业务数据在同一个事务内原子提交。后台线程轮询 outbox 表，将未发送的消息投递到 Kafka。

```
placeOrder() 事务内:
  1. orderRepository.save(order)     ← 写 orders 表
  2. outboxRepository.save(event)    ← 写 outbox 表（替代直接发 Kafka）
  事务提交 ✅ 原子性保证

OutboxPoller（后台线程）:
  3. SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at
  4. kafkaTemplate.send(event)
  5. UPDATE outbox_events SET status = 'SENT', sent_at = NOW()
```

### 1.3 数据模型

```sql
CREATE TABLE outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,                          -- orderId
    event_type   VARCHAR(100) NOT NULL,                         -- WMS_INSTRUCTION_REQUIRED 等
    payload      TEXT NOT NULL,                                  -- JSON 序列化的事件体
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',        -- PENDING / SENT / FAILED
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at      TIMESTAMP,
    retry_count  INT NOT NULL DEFAULT 0,
    max_retries  INT NOT NULL DEFAULT 5
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
```

### 1.4 架构适配

**六边形架构保护**：`DomainEventPublisher` 接口不变，只替换实现。

| 组件 | 变更 |
|------|------|
| `DomainEventPublisher` | 接口不变 |
| `SpringDomainEventPublisher` | 替换为 `OutboxEventPublisher`：事务内写 outbox 表 |
| `OutboxEventEntity` | 新增 JPA 实体映射 `outbox_events` 表 |
| `OutboxEventJpaRepository` | 新增 JPA Repository |
| `OutboxPoller` | 新增 `@Scheduled` 轮询 + Kafka 投递组件 |
| `@TransactionalEventListener` | 保留，但 handler 内不再直接调 `wmsPort.sendInstruction()`，改为由 OutboxPoller 异步投递。Saga handler 只负责写 DB 状态 + 写 outbox 事件。 |

### 1.5 OutboxPoller 设计

```java
@Component
public class OutboxPoller {
    private final OutboxEventJpaRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:5000}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEventEntity> pending = outboxRepo
            .findTop50ByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxEventEntity event : pending) {
            try {
                kafkaTemplate.send(
                    topicFor(event.getEventType()),
                    event.getAggregateId(),
                    event.getPayload()
                ).get(5, TimeUnit.SECONDS);  // 同步等待确认，简化实现；高吞吐场景可改为异步回调

                event.setStatus("SENT");
                event.setSentAt(LocalDateTime.now());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    event.setStatus("FAILED");
                }
            }
        }
    }
}
```

### 1.6 与 Saga 的交互变化

`onWmsRequired` / `onTmsRequired` 不再直接调 `wmsPort.sendInstruction()`。事件写入 outbox 后由 OutboxPoller 异步投递，WMS 回调触发 saga 下一步。

**好处**：
- 消息不丢失（事务保证）
- 自动重试（retry_count + max_retries）
- 失败可观测（FAILED 状态 + metrics）

---

## 2. 乐观锁 + 审计日志

### 2.1 问题

当前 `OrderEntity` 没有 `version` 字段，`updateStatus()` 是无条件 UPDATE：

```java
@Modifying
@Query("UPDATE OrderEntity o SET o.status = :status WHERE o.id = :id")
void updateStatus(@Param("id") String id, @Param("status") String status);
```

**风险**：
- 两个并发请求同时更新同一订单状态 → 丢失更新（last-write-wins）
- 无法追踪"谁在什么时候把状态从 A 改成了 B"
- 无法做条件更新（如"只有 CREATED 状态才能改为 WMS_ACKED"）

### 2.2 乐观锁方案

**2.2.1 JPA @Version**

```java
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Version
    @Column(name = "version")
    private Long version;
    // ... 其余字段不变
}
```

**2.2.2 条件更新**

`updateStatus` 改为带状态前置条件的更新：

```sql
UPDATE orders
SET status = :newStatus, version = version + 1
WHERE id = :id
  AND status = :expectedStatus
  AND version = :expectedVersion
```

如果 affected rows = 0，说明并发冲突或状态前置条件不满足，抛出 `OptimisticLockingFailureException`。

**2.2.3 领域层适配**

```java
// Order 领域模型新增 version
public class Order {
    private final Long version;  // 新增
    // ...

    public Order transitionTo(OrderStatus newStatus) {
        // 状态机校验
        if (!allowedTransitions.get(status).contains(newStatus)) {
            throw new IllegalOrderStateException(status, newStatus);
        }
        return new Order(orderId, customerId, items, newStatus,
                         idempotencyKey, reservationId, createdAt,
                         allReservationIds, version);
    }
}
```

```java
// OrderRepositoryPort 新增方法
public interface OrderRepositoryPort {
    void save(Order order);
    Optional<Order> findById(String orderId);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    boolean updateStatusWithVersion(String orderId, OrderStatus newStatus,
                                     OrderStatus expectedStatus, long expectedVersion);
}
```

**2.2.4 Flyway migration**

```sql
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

### 2.3 审计日志方案

**不新建 `audit_log` 表**——复用 `saga_logs` 已有结构，扩展字段：

```sql
ALTER TABLE saga_logs ADD COLUMN previous_status VARCHAR(32);
ALTER TABLE saga_logs ADD COLUMN new_status VARCHAR(32);
ALTER TABLE saga_logs ADD COLUMN changed_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM';
```

**理由**：
- `saga_logs` 已经记录了每步的状态变更（`step_status` + `detail` + `started_at`/`completed_at`）
- 订单状态变更总是发生在 saga 步骤内，不需要独立的审计表
- 减少表数量，降低维护成本

**SagaLogPort 扩展**：

```java
public interface SagaLogPort {
    // ... 现有方法不变

    void recordSagaStepCompleted(String orderId, String stepName, String message,
                                  String previousStatus, String newStatus);
    void recordSagaStepFailed(String orderId, String stepName, String error,
                               String previousStatus, String newStatus);
}
```

---

## 3. 轻量级 CQRS 读模型

### 3.1 问题

当前所有查询都走 `OrderJpaRepository`，直接映射 `OrderEntity`。随着查询需求增长：
- `OrderEntity` 需要加各种查询专用的字段/索引
- Repository 接口膨胀（`findByCustomerIdAndStatusAndCreatedAtBetween...`）
- 写模型和读模型的优化方向冲突（写要规范化，读要反规范化）

### 3.2 方案

**不引入** Event Sourcing、不引入消息总线分离读写服务。只在同一个应用内，用数据库视图 + 专用查询 Repository 实现读写分离。

### 3.3 读模型视图

```sql
CREATE VIEW order_view AS
SELECT
    o.id,
    o.customer_id,
    o.status,
    o.created_at,
    o.idempotency_key,
    o.items,
    o.version,
    COUNT(ir.reservation_id) AS reservation_count,
    COALESCE(SUM(ir.quantity), 0) AS total_quantity,
    latest_sl.step_name AS last_saga_step,
    latest_sl.step_status AS last_saga_status,
    latest_sl.created_at AS last_saga_step_at
FROM orders o
LEFT JOIN inventory_reservation ir ON ir.order_id = o.id
LEFT JOIN LATERAL (
    SELECT step_name, step_status, created_at
    FROM saga_logs sl
    WHERE sl.order_id = o.id
    ORDER BY sl.created_at DESC
    LIMIT 1
) latest_sl ON true
GROUP BY o.id, o.customer_id, o.status, o.created_at,
         o.idempotency_key, o.items, o.version,
         latest_sl.step_name, latest_sl.step_status, latest_sl.created_at;
```

> 注：H2 不支持 `LATERAL`，生产用 PostgreSQL。H2 测试环境用简化版视图（子查询 + `ROW_NUMBER()` 窗口函数替代，Flyway 可按 profile 选择不同 migration）。

### 3.4 查询端口（order-application 层）

```java
// 查询条件 DTO
public record OrderSearchCriteria(
    String customerId,
    String status,
    Instant createdAfter,
    Instant createdBefore,
    int page,
    int size
) {}

// 查询结果 DTO — 纯读模型，不污染领域模型
public record OrderSummary(
    String orderId, String customerId, String status,
    Instant createdAt, int reservationCount, int totalQuantity,
    String lastSagaStep, String lastSagaStatus
) {}

public record OrderDetail(
    OrderSummary summary,
    List<SagaStepView> sagaSteps
) {}

public record SagaStepView(
    String stepName, String stepStatus,
    Instant startedAt, Instant completedAt, String detail
) {}

// 查询端口接口
public interface OrderQueryPort {
    Page<OrderSummary> search(OrderSearchCriteria criteria);
    Optional<OrderDetail> findDetail(String orderId);
}
```

### 3.5 适配层实现

```java
// order-adapter 层
@Entity
@Table(name = "order_view")
@Immutable  // JPA 只读标记
public class OrderViewEntity {
    @Id @Column(name = "id")
    private String id;
    @Column(name = "customer_id") private String customerId;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "reservation_count") private int reservationCount;
    @Column(name = "total_quantity") private int totalQuantity;
    @Column(name = "last_saga_step") private String lastSagaStep;
    @Column(name = "last_saga_status") private String lastSagaStatus;
    // ... getters
}

public interface OrderViewRepository extends JpaRepository<OrderViewEntity, String> {
    // 动态查询用 Specification 或 QueryDSL
}
```

### 3.6 写模型保持不变

`OrderRepositoryPort` + `OrderJpaRepository` 继续负责写操作（save、updateStatus），不受查询需求影响。

### 3.7 关键约束

- **最终一致**：视图基于同一数据库，延迟在毫秒级，对订单系统可接受
- **不引入跨服务 CQRS**：那是 Event Sourcing 的配套方案，当前不需要
- **数据库视图由 Flyway 管理**：与应用代码同步演进
- **H2 兼容**：测试环境用简化版视图（无 LATERAL）

---

## 4. 实施顺序

| 步骤 | 内容 | 优先级 | 理由 |
|------|------|--------|------|
| 1 | Outbox Pattern | 🔴 高 | 解决当前最严重的数据一致性缺陷 |
| 2 | 乐观锁 + 审计日志 | 🟡 中 | 增强写安全，成本较低 |
| 3 | CQRS 读模型 | 🟢 低 | 为复杂查询预留空间，当前查询需求简单 |

### 步骤 1 详细任务

| 任务 | 涉及 |
|------|------|
| 1.1 Flyway V6: 创建 outbox_events 表 | order-infrastructure |
| 1.2 OutboxEventEntity + OutboxEventJpaRepository | order-adapter |
| 1.3 OutboxEventPublisher 替代 SpringDomainEventPublisher | order-infrastructure |
| 1.4 OutboxPoller + @Scheduled 轮询 | order-adapter |
| 1.5 重构 Saga 事件发布：从直接发 Kafka 改为写 outbox | order-application |
| 1.6 Outbox metrics（pending count, send latency, failure count） | order-adapter |
| 1.7 测试 | 全部 |

### 步骤 2 详细任务

| 任务 | 涉及 |
|------|------|
| 2.1 Flyway V7: orders 表加 version 列 | order-infrastructure |
| 2.2 Flyway V8: saga_logs 加 previous_status, new_status, changed_by | order-infrastructure |
| 2.3 OrderEntity 加 @Version, Order 领域模型加 version | order-adapter + order-application |
| 2.4 OrderRepositoryPort 新增 updateStatusWithVersion | order-application |
| 2.5 OrderPersistenceAdapter 实现条件更新 | order-adapter |
| 2.6 SagaLogPort 扩展签名，SagaLogPersistenceAdapter 适配 | order-application + order-adapter |
| 2.7 OrderPlacementSaga 使用条件更新 | order-application |
| 2.8 测试 | 全部 |

### 步骤 3 详细任务

| 任务 | 涉及 |
|------|------|
| 3.1 Flyway V9: 创建 order_view 视图 | order-infrastructure |
| 3.2 OrderQueryPort + DTO 定义 | order-application |
| 3.3 OrderViewEntity + OrderViewRepository | order-adapter |
| 3.4 OrderQueryAdapter 实现 | order-adapter |
| 3.5 OrderController 新增查询端点 | order-adapter |
| 3.6 测试 | 全部 |

---

## 5. 不做的事

| 模式 | 理由 |
|------|------|
| **Event Sourcing** | ROI 低。订单状态机简单（5 个状态），事件溯源的复杂度远超收益。saga_logs 已提供足够的历史追溯。 |
| **CDC (Debezium)** | 当前没有数据仓库/数据湖需求。Outbox Pattern 已提供可靠的事件投递，CDC 是锦上添花。 |
| **Snapshot Pattern** | 不引入 Event Sourcing 就不需要快照。 |
| **跨服务 CQRS** | 当前是单体应用，读写分离在数据库视图层面足够。 |
