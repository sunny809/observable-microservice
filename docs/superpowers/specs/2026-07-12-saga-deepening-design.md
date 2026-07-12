# Saga 模式深化设计文档

## 概述

为 order-demo 的 `OrderPlacementSaga` 添加生产级 Saga 持久化、超时探测和补偿幂等性，解决当前实现中 JVM 崩溃后 saga 状态丢失、挂起 saga 无人处理、补偿重复执行等关键问题。

## 目标

1. **Saga 持久化** — 扩展 `saga_logs` 表存储 saga 实例状态和步骤状态机
2. **超时探测** — 通过 `@Scheduled` 定时任务检测超时 saga 并触发补偿
3. **补偿幂等性** — 通过 `compensation_logs` 表确保补偿操作只执行一次

## 当前状态

- Saga 使用 `@TransactionalEventListener(AFTER_COMMIT)` 异步编排
- 已有 `SagaLogPort` 记录审计日志（step/message）
- `Order` 实体有 `status` 字段（CREATED/WMS_ACKED/WMS_PICKED/TMS_DISPATCHED/REJECTED/TMS_REJECTED）
- 补偿通过 `releaseAll()` / `releaseAllAsync()` 实现
- 异步回调使用 `TransactionTemplate` 执行 DB 操作

## 数据模型

### 扩展 saga_logs 表

```sql
ALTER TABLE saga_logs
    ADD COLUMN saga_type VARCHAR(50) NOT NULL DEFAULT 'ORDER_PLACEMENT',
    ADD COLUMN step_name VARCHAR(50),
    ADD COLUMN step_status VARCHAR(20),  -- PENDING, COMPLETED, FAILED, COMPENSATING, COMPENSATED, COMPENSATION_REQUIRED
    ADD COLUMN started_at TIMESTAMP,
    ADD COLUMN completed_at TIMESTAMP,
    ADD COLUMN compensation_status VARCHAR(20),  -- NOT_REQUIRED, PENDING, COMPLETED, FAILED
    ADD COLUMN retry_count INT DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP;

CREATE INDEX idx_saga_logs_status_started_at ON saga_logs(step_status, started_at);
CREATE INDEX idx_saga_logs_order_id ON saga_logs(order_id);
```

### 新建 compensation_logs 表

```sql
CREATE TABLE compensation_logs (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    step_name VARCHAR(50) NOT NULL,
    reservation_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,  -- COMPLETED, FAILED
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_compensation_logs_order_id ON compensation_logs(order_id);
```

## 状态机

### Saga Instance 状态

```
STARTED → IN_PROGRESS → COMPLETED
    ↓           ↓
    └───── COMPENSATION_REQUIRED ──→ COMPENSATING ──→ COMPENSATED / COMPENSATION_FAILED
```

### Step Status

| 状态 | 说明 |
|------|------|
| PENDING | 步骤已启动，等待完成 |
| COMPLETED | 步骤成功完成 |
| FAILED | 步骤执行失败 |
| COMPENSATION_REQUIRED | 超时或失败，需要补偿 |
| COMPENSATING | 补偿正在进行 |
| COMPENSATED | 补偿成功完成 |

## 架构

```
┌─────────────────────────────────────────────────────────┐
│                    OrderPlacementSaga                    │
│  (Orchestrator — @TransactionalEventListener chain)      │
└─────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌──────────┐   ┌──────────┐   ┌──────────────┐
    │ SagaLog  │   │ Metrics  │   │ Compensation │
    │ Port     │   │ Port     │   │ Log Port     │
    └────┬─────┘   └────┬─────┘   └──────┬───────┘
         │              │                │
         ▼              ▼                ▼
    ┌──────────┐   ┌──────────┐   ┌──────────────┐
    │saga_logs │   │Prometheus│   │compensation_ │
    │(extended)│   │(metrics) │   │logs         │
    └──────────┘   └──────────┘   └──────────────┘
         ▲
         │
    ┌────┴──────────────────────────────┐
    │      SagaTimeoutDetector            │
    │  (@Scheduled — 每 60s 扫描超时)       │
    └─────────────────────────────────────┘
```

## 组件设计

### 1. SagaLogPort 扩展

新增方法：

```java
void recordSagaStepStarted(String orderId, String stepName);
void recordSagaStepCompleted(String orderId, String stepName, String message);
void recordSagaStepFailed(String orderId, String stepName, String error);
void recordSagaCompensationRequired(String orderId, String stepName, String reason);
void recordSagaCompensationStarted(String orderId, String stepName);
void recordSagaCompensationCompleted(String orderId, String stepName);
void recordSagaCompensationFailed(String orderId, String stepName, String error);
List<SagaLog> findPendingStepsOlderThan(Duration timeout);
```

### 2. CompensationLogPort (新建)

```java
boolean exists(String idempotencyKey);
void save(String idempotencyKey, String orderId, String stepName, 
          String reservationId, CompensationStatus status, String errorMessage);
```

### 3. SagaTimeoutDetector (新建)

```java
@Component
public class SagaTimeoutDetector {
    
    @Scheduled(fixedDelayString = "${app.saga.timeout.check-interval:60000}")
    public void detectTimeouts() {
        // 1. 查询超时的 PENDING 步骤
        // 2. 标记为 COMPENSATION_REQUIRED
        // 3. 发布 SagaCompensationRequiredEvent
    }
}
```

### 4. SagaCompensationRequiredEvent (新建)

```java
public class SagaCompensationRequiredEvent {
    private final String orderId;
    private final String stepName;
    private final String reason;  // TIMEOUT or FAILURE
    private final List<InventoryReservation> reservations;
}
```

## 超时配置

```yaml
app:
  saga:
    timeout:
      check-interval: 60s
      default-timeout: 300s
      step-timeouts:
        ORDER_CREATED: 60s    # 订单创建后 60s 内必须完成 WMS 确认
        WMS_ACKED: 180s       # WMS 确认后 180s 内必须完成拣货
        WMS_PICKED: 120s      # 拣货后 120s 内必须完成 TMS 调度
```

## 补偿幂等性

### 幂等键生成

```java
private String compensationIdempotencyKey(String orderId, String stepName, String reservationId) {
    return String.format("compensate:%s:%s:%s", orderId, stepName, reservationId);
}
```

### 补偿流程

```java
private void releaseAll(List<InventoryReservation> reservations, String stepName) {
    for (InventoryReservation r : reservations) {
        String key = compensationIdempotencyKey(r.getOrderId(), stepName, r.getReservationId());
        
        if (compensationLogPort.exists(key)) {
            sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(), "Skip: already compensated");
            continue;
        }
        
        try {
            inventoryPort.release(r.getReservationId()).join();
            compensationLogPort.save(key, r.getOrderId(), stepName, r.getReservationId(), COMPLETED, null);
        } catch (Exception e) {
            compensationLogPort.save(key, r.getOrderId(), stepName, r.getReservationId(), FAILED, e.getMessage());
            sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                "Release failed during compensation: " + e.getMessage());
        }
    }
}
```

## 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `db/migration/V4__extend_saga_logs.sql` | 新增 | 扩展 saga_logs 表 |
| `db/migration/V5__create_compensation_logs.sql` | 新增 | 新建补偿日志表 |
| `application.port.out.SagaLogPort` | 修改 | 扩展接口方法 |
| `application.port.out.CompensationLogPort` | 新增 | 补偿日志端口 |
| `adapter.outbound.persistence.SagaLogEntity` | 修改 | 扩展实体字段 |
| `adapter.outbound.persistence.SagaLogJpaRepository` | 修改 | 添加查询方法 |
| `adapter.outbound.persistence.SagaLogPersistenceAdapter` | 修改 | 实现新接口方法 |
| `adapter.outbound.persistence.CompensationLogEntity` | 新增 | 补偿日志实体 |
| `adapter.outbound.persistence.CompensationLogJpaRepository` | 新增 | 补偿日志仓库 |
| `adapter.outbound.persistence.CompensationLogPersistenceAdapter` | 新增 | 补偿日志适配器 |
| `application.service.SagaTimeoutDetector` | 新增 | 超时检测器 |
| `application.domain.SagaCompensationRequiredEvent` | 新增 | 补偿事件 |
| `application.service.OrderPlacementSaga` | 修改 | 集成持久化和幂等性 |
| `application.yml` | 修改 | 添加超时配置 |
| `OrderPlacementSagaTest` | 修改 | 添加持久化和超时测试 |
| `SagaTimeoutDetectorTest` | 新增 | 超时检测器测试 |

## 测试策略

1. **单元测试：** SagaLogPort 方法调用验证、CompensationLogPort 幂等性验证
2. **集成测试：** 超时检测器扫描 + 补偿触发流程
3. **BDD 场景：** 超时 saga 自动补偿、重复补偿幂等性

## 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| 定时任务与 saga 执行并发冲突 | 使用数据库行级锁（SELECT FOR UPDATE） |
| 补偿日志表无限增长 | 定期清理已完成超过 30 天的记录 |
| 超时时间配置不当 | 提供合理的默认值 + 环境覆盖能力 |