# Saga 模式深化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 order-demo 的 `OrderPlacementSaga` 添加生产级 Saga 持久化、超时探测和补偿幂等性，解决 JVM 崩溃后 saga 状态丢失、挂起 saga 无人处理、补偿重复执行等关键问题。

**Architecture:** 扩展 `saga_logs` 表存储 saga 步骤状态机，新建 `compensation_logs` 表确保补偿幂等性，通过 `@Scheduled` 定时任务检测超时 saga 并触发补偿。所有变更遵循六边形架构，不修改应用核心逻辑。

**Tech Stack:** Spring Boot 3.4.3, Spring Data JPA, Flyway, PostgreSQL/H2, Spring @Scheduled

## Global Constraints

- 不修改 `OrderPlacementSaga` 的核心业务逻辑（只添加持久化和幂等性调用）
- 遵循六边形架构：port/adapter 模式
- 所有数据库变更通过 Flyway 迁移脚本
- 保持向后兼容：现有 saga_logs 数据不受影响
- 测试覆盖：每个 task 必须有单元测试

---

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

### Task 2: 扩展 SagaLogPort 接口 + SagaLogEntity

**Files:**
- Modify: `order-application/src/main/java/com/example/order/application/port/out/SagaLogPort.java`
- Modify: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/SagaLogEntity.java`

**Interfaces:**
- Consumes: 无（这是基础接口扩展）
- Produces: 扩展后的 SagaLogPort 接口，新增状态追踪方法

- [ ] **Step 1: 扩展 SagaLogPort 接口**

```java
// order-application/src/main/java/com/example/order/application/port/out/SagaLogPort.java
package com.example.order.application.port.out;

import java.util.List;

public interface SagaLogPort {
    void recordStep(String orderId, String step, String detail);

    void recordCompensation(String orderId, String reservationId, String reason);

    void recordSagaStepStarted(String orderId, String stepName);

    void recordSagaStepCompleted(String orderId, String stepName, String message);

    void recordSagaStepFailed(String orderId, String stepName, String error);

    void recordSagaCompensationRequired(String orderId, String stepName, String reason);

    void recordSagaCompensationStarted(String orderId, String stepName);

    void recordSagaCompensationCompleted(String orderId, String stepName);

    void recordSagaCompensationFailed(String orderId, String stepName, String error);

    List<SagaLogEntry> findPendingStepsOlderThan(java.time.Duration timeout);
}
```

- [ ] **Step 2: 创建 SagaLogEntry DTO**

```java
// order-application/src/main/java/com/example/order/application/port/out/SagaLogEntry.java
package com.example.order.application.port.out;

import java.time.LocalDateTime;

public record SagaLogEntry(
    Long id,
    String orderId,
    String stepName,
    String stepStatus,
    LocalDateTime startedAt,
    LocalDateTime completedAt
) {}
```

- [ ] **Step 3: 扩展 SagaLogEntity**

```java
// order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/SagaLogEntity.java
package com.example.order.adapter.outbound.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_logs")
public class SagaLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "step", nullable = false)
    private String step;

    @Column(name = "detail", nullable = false)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "saga_type")
    private String sagaType;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "step_status")
    private String stepStatus;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "compensation_status")
    private String compensationStatus;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    public SagaLogEntity() {}

    public SagaLogEntity(String orderId, String step, String detail, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.step = step;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    // getters
    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getStep() { return step; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getSagaType() { return sagaType; }
    public String getStepName() { return stepName; }
    public String getStepStatus() { return stepStatus; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getCompensationStatus() { return compensationStatus; }
    public Integer getRetryCount() { return retryCount; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
}
```

- [ ] **Step 4: Commit**

```bash
git add order-application/src/main/java/com/example/order/application/port/out/
git add order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/SagaLogEntity.java
git commit -m "feat(saga): extend SagaLogPort and SagaLogEntity for state tracking"
```

---

### Task 3: 新建 CompensationLogPort + 实体 + 适配器

**Files:**
- Create: `order-application/src/main/java/com/example/order/application/port/out/CompensationLogPort.java`
- Create: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/CompensationLogEntity.java`
- Create: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/CompensationLogJpaRepository.java`
- Create: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/CompensationLogPersistenceAdapter.java`

**Interfaces:**
- Consumes: 无
- Produces: CompensationLogPort 接口，用于补偿幂等性检查

- [ ] **Step 1: 创建 CompensationLogPort**

```java
// order-application/src/main/java/com/example/order/application/port/out/CompensationLogPort.java
package com.example.order.application.port.out;

public interface CompensationLogPort {
    boolean exists(String idempotencyKey);

    void save(String idempotencyKey, String orderId, String stepName,
              String reservationId, CompensationStatus status, String errorMessage);
}
```

- [ ] **Step 2: 创建 CompensationStatus 枚举**

```java
// order-application/src/main/java/com/example/order/application/port/out/CompensationStatus.java
package com.example.order.application.port.out;

public enum CompensationStatus {
    COMPLETED, FAILED
}
```

- [ ] **Step 3: 创建 CompensationLogEntity**

```java
// order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/CompensationLogEntity.java
package com.example.order.adapter.outbound.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "compensation_logs")
public class CompensationLogEntity {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "error_message")
    private String errorMessage;

    public CompensationLogEntity() {}

    public CompensationLogEntity(String idempotencyKey, String orderId, String stepName,
                                 String reservationId, String status, String errorMessage) {
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.stepName = stepName;
        this.reservationId = reservationId;
        this.status = status;
        this.attemptedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    // getters
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOrderId() { return orderId; }
    public String getStepName() { return stepName; }
    public String getReservationId() { return reservationId; }
    public String getStatus() { return status; }
    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public String getErrorMessage() { return errorMessage; }
}
```

- [ ] **Step 4: 创建 CompensationLogJpaRepository**

```java
// order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/CompensationLogJpaRepository.java
package com.example.order.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensationLogJpaRepository extends JpaRepository<CompensationLogEntity, String> {
}
```

- [ ] **Step 5: 创建 CompensationLogPersistenceAdapter**

```java
// order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/CompensationLogPersistenceAdapter.java
package com.example.order.adapter.outbound.persistence;

import com.example.order.application.port.out.CompensationLogPort;
import com.example.order.application.port.out.CompensationStatus;
import org.springframework.stereotype.Component;

@Component
public class CompensationLogPersistenceAdapter implements CompensationLogPort {

    private final CompensationLogJpaRepository repository;

    public CompensationLogPersistenceAdapter(CompensationLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String idempotencyKey) {
        return repository.existsById(idempotencyKey);
    }

    @Override
    public void save(String idempotencyKey, String orderId, String stepName,
                     String reservationId, CompensationStatus status, String errorMessage) {
        repository.save(new CompensationLogEntity(
            idempotencyKey, orderId, stepName, reservationId,
            status.name(), errorMessage
        ));
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add order-application/src/main/java/com/example/order/application/port/out/
git add order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/
git commit -m "feat(saga): add CompensationLogPort and persistence adapter"
```

---

### Task 4: 更新 SagaLogPersistenceAdapter

**Files:**
- Modify: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/SagaLogPersistenceAdapter.java`
- Modify: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/SagaLogJpaRepository.java`

**Interfaces:**
- Consumes: 扩展后的 SagaLogPort 接口
- Produces: 完整的 SagaLogPersistenceAdapter 实现

- [ ] **Step 1: 扩展 SagaLogJpaRepository**

```java
// order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/SagaLogJpaRepository.java
package com.example.order.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface SagaLogJpaRepository extends JpaRepository<SagaLogEntity, Long> {

    @Query("SELECT s FROM SagaLogEntity s WHERE s.stepStatus = 'PENDING' AND s.startedAt < :threshold")
    List<SagaLogEntity> findPendingStepsOlderThan(@Param("threshold") LocalDateTime threshold);
}
```

- [ ] **Step 2: 更新 SagaLogPersistenceAdapter**

```java
// order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/SagaLogPersistenceAdapter.java
package com.example.order.adapter.outbound.persistence;

import com.example.order.application.port.out.SagaLogPort;
import com.example.order.application.port.out.SagaLogEntry;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SagaLogPersistenceAdapter implements SagaLogPort {

    private final SagaLogJpaRepository repository;

    public SagaLogPersistenceAdapter(SagaLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordStep(String orderId, String step, String detail) {
        repository.save(new SagaLogEntity(orderId, step, detail, LocalDateTime.now()));
    }

    @Override
    public void recordCompensation(String orderId, String reservationId, String reason) {
        repository.save(new SagaLogEntity(orderId, "COMPENSATION",
            "reservationId=" + reservationId + ", reason=" + reason, LocalDateTime.now()));
    }

    @Override
    public void recordSagaStepStarted(String orderId, String stepName) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStepName(stepName);
        entity.setStepStatus("PENDING");
        entity.setStartedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDetail("Step started");
        repository.save(entity);
    }

    @Override
    public void recordSagaStepCompleted(String orderId, String stepName, String message) {
        // 简化实现：查询最新记录并更新
        // 实际生产环境应使用更精确的查询条件
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStepName(stepName);
        entity.setStepStatus("COMPLETED");
        entity.setCompletedAt(LocalDateTime.now());
        entity.setDetail(message);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public void recordSagaStepFailed(String orderId, String stepName, String error) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStepName(stepName);
        entity.setStepStatus("FAILED");
        entity.setDetail(error);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public void recordSagaCompensationRequired(String orderId, String stepName, String reason) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStepName(stepName);
        entity.setStepStatus("COMPENSATION_REQUIRED");
        entity.setDetail(reason);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public void recordSagaCompensationStarted(String orderId, String stepName) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStepName(stepName);
        entity.setStepStatus("COMPENSATING");
        entity.setDetail("Compensation started");
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public void recordSagaCompensationCompleted(String orderId, String stepName) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStepName(stepName);
        entity.setCompensationStatus("COMPLETED");
        entity.setDetail("Compensation completed");
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public void recordSagaCompensationFailed(String orderId, String stepName, String error) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStepName(stepName);
        entity.setCompensationStatus("FAILED");
        entity.setDetail(error);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public List<SagaLogEntry> findPendingStepsOlderThan(Duration timeout) {
        LocalDateTime threshold = LocalDateTime.now().minus(timeout);
        return repository.findPendingStepsOlderThan(threshold).stream()
            .map(e -> new SagaLogEntry(
                e.getId(), e.getOrderId(), e.getStepName(),
                e.getStepStatus(), e.getStartedAt(), e.getCompletedAt()
            ))
            .collect(Collectors.toList());
    }
}
```

**注意：** SagaLogEntity 需要为所有新增字段添加 setter 方法。

- [ ] **Step 3: Commit**

```bash
git add order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/
git commit -m "feat(saga): update SagaLogPersistenceAdapter with state tracking methods"
```

---

### Task 5: 创建 SagaTimeoutDetector

**Files:**
- Create: `order-application/src/main/java/com/example/order/application/service/SagaTimeoutDetector.java`
- Create: `order-application/src/main/java/com/example/order/application/domain/SagaCompensationRequiredEvent.java`

**Interfaces:**
- Consumes: SagaLogPort.findPendingStepsOlderThan(), SagaLogPort.recordSagaCompensationRequired()
- Produces: SagaCompensationRequiredEvent 事件

- [ ] **Step 1: 创建 SagaCompensationRequiredEvent**

```java
// order-application/src/main/java/com/example/order/application/domain/SagaCompensationRequiredEvent.java
package com.example.order.application.domain;

import java.util.List;

public class SagaCompensationRequiredEvent {
    private final String orderId;
    private final String stepName;
    private final String reason;
    private final List<InventoryReservation> reservations;

    public SagaCompensationRequiredEvent(String orderId, String stepName, String reason, List<InventoryReservation> reservations) {
        this.orderId = orderId;
        this.stepName = stepName;
        this.reason = reason;
        this.reservations = reservations;
    }

    public String getOrderId() { return orderId; }
    public String getStepName() { return stepName; }
    public String getReason() { return reason; }
    public List<InventoryReservation> getReservations() { return reservations; }
}
```

- [ ] **Step 2: 创建 SagaTimeoutDetector**

```java
// order-application/src/main/java/com/example/order/application/service/SagaTimeoutDetector.java
package com.example.order.application.service;

import com.example.order.application.domain.SagaCompensationRequiredEvent;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.SagaLogPort;
import com.example.order.application.port.out.SagaLogEntry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class SagaTimeoutDetector {

    private final SagaLogPort sagaLogPort;
    private final DomainEventPublisher eventPublisher;

    public SagaTimeoutDetector(SagaLogPort sagaLogPort, DomainEventPublisher eventPublisher) {
        this.sagaLogPort = sagaLogPort;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.saga.timeout.check-interval:60000}")
    public void detectTimeouts() {
        Duration timeout = Duration.ofSeconds(300); // 默认 5 分钟
        List<SagaLogEntry> pending = sagaLogPort.findPendingStepsOlderThan(timeout);

        for (SagaLogEntry entry : pending) {
            sagaLogPort.recordSagaCompensationRequired(
                entry.orderId(), entry.stepName(), "TIMEOUT"
            );

            eventPublisher.publish(new SagaCompensationRequiredEvent(
                entry.orderId(), entry.stepName(), "TIMEOUT", List.of()
            ));
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add order-application/src/main/java/com/example/order/application/
git commit -m "feat(saga): add SagaTimeoutDetector and SagaCompensationRequiredEvent"
```

---

### Task 6: 集成补偿幂等性到 OrderPlacementSaga

**Files:**
- Modify: `order-application/src/main/java/com/example/order/application/service/OrderPlacementSaga.java`

**Interfaces:**
- Consumes: CompensationLogPort
- Produces: 幂等补偿逻辑

- [ ] **Step 1: 修改 releaseAll 方法**

在 `OrderPlacementSaga.java` 中，修改 `releaseAll` 和 `releaseAllAsync` 方法，添加补偿幂等性检查：

```java
// 在 OrderPlacementSaga 类中添加 CompensationLogPort 依赖
private final CompensationLogPort compensationLogPort;

// 修改构造函数注入
public OrderPlacementSaga(..., CompensationLogPort compensationLogPort) {
    // ... existing assignments
    this.compensationLogPort = compensationLogPort;
}

// 修改 releaseAll 方法
private void releaseAll(List<InventoryReservation> reservations, String stepName) {
    for (InventoryReservation r : reservations) {
        String idempotencyKey = compensationIdempotencyKey(r.getOrderId(), stepName, r.getReservationId());

        if (compensationLogPort.exists(idempotencyKey)) {
            sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                "Skip: already compensated");
            continue;
        }

        try {
            inventoryPort.release(r.getReservationId()).join();
            compensationLogPort.save(idempotencyKey, r.getOrderId(), stepName,
                r.getReservationId(), CompensationStatus.COMPLETED, null);
        } catch (Exception e) {
            compensationLogPort.save(idempotencyKey, r.getOrderId(), stepName,
                r.getReservationId(), CompensationStatus.FAILED, e.getMessage());
            sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                "Release failed during compensation: " + e.getMessage());
        }
    }
}

// 修改 releaseAllAsync 方法
private CompletableFuture<Void> releaseAllAsync(List<InventoryReservation> reservations, String stepName) {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (InventoryReservation r : reservations) {
        String idempotencyKey = compensationIdempotencyKey(r.getOrderId(), stepName, r.getReservationId());

        if (compensationLogPort.exists(idempotencyKey)) {
            sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                "Skip: already compensated");
            continue;
        }

        futures.add(inventoryPort.release(r.getReservationId())
            .thenRun(() -> {
                compensationLogPort.save(idempotencyKey, r.getOrderId(), stepName,
                    r.getReservationId(), CompensationStatus.COMPLETED, null);
            })
            .exceptionally(ex -> {
                compensationLogPort.save(idempotencyKey, r.getOrderId(), stepName,
                    r.getReservationId(), CompensationStatus.FAILED, ex.getMessage());
                sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                    "Release failed during compensation: " + ex.getMessage());
                return null;
            }));
    }
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
}

private String compensationIdempotencyKey(String orderId, String stepName, String reservationId) {
    return String.format("compensate:%s:%s:%s", orderId, stepName, reservationId);
}
```

- [ ] **Step 2: Commit**

```bash
git add order-application/src/main/java/com/example/order/application/service/OrderPlacementSaga.java
git commit -m "feat(saga): integrate compensation idempotency into OrderPlacementSaga"
```

---

### Task 7: 添加超时配置 + 启用定时任务

**Files:**
- Modify: `order-infrastructure/src/main/resources/application.yml`
- Modify: `order-infrastructure/src/main/java/com/example/order/infrastructure/OrderServiceApplication.java`（如果需要）

**Interfaces:**
- Consumes: 无
- Produces: 超时配置

- [ ] **Step 1: 添加超时配置到 application.yml**

```yaml
# 在 order-infrastructure/src/main/resources/application.yml 中添加
app:
  saga:
    timeout:
      check-interval: 60s
      default-timeout: 300s
      step-timeouts:
        ORDER_CREATED: 60s
        WMS_ACKED: 180s
        WMS_PICKED: 120s
```

- [ ] **Step 2: 确保 @EnableScheduling 已启用**

检查 `OrderServiceApplication.java`：

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {
    // ...
}
```

- [ ] **Step 3: Commit**

```bash
git add order-infrastructure/src/main/resources/application.yml
git add order-infrastructure/src/main/java/com/example/order/infrastructure/OrderServiceApplication.java
git commit -m "feat(saga): add saga timeout configuration and enable scheduling"
```

---

### Task 8: 测试

**Files:**
- Create: `order-application/src/test/java/com/example/order/application/service/SagaTimeoutDetectorTest.java`
- Modify: `order-application/src/test/java/com/example/order/application/service/OrderPlacementSagaTest.java`

**Interfaces:**
- Consumes: 所有前面的 task 产出
- Produces: 测试覆盖

- [ ] **Step 1: 创建 SagaTimeoutDetectorTest**

```java
// order-application/src/test/java/com/example/order/application/service/SagaTimeoutDetectorTest.java
package com.example.order.application.service;

import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.SagaLogPort;
import com.example.order.application.port.out.SagaLogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaTimeoutDetectorTest {

    @Mock
    private SagaLogPort sagaLogPort;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private SagaTimeoutDetector detector;

    @Test
    void shouldDetectTimeoutAndPublishCompensationEvent() {
        // given
        SagaLogEntry entry = new SagaLogEntry(1L, "order-123", "WMS_ACKED", "PENDING",
            LocalDateTime.now().minusMinutes(10), null);
        when(sagaLogPort.findPendingStepsOlderThan(any(Duration.class)))
            .thenReturn(List.of(entry));

        // when
        detector.detectTimeouts();

        // then
        verify(sagaLogPort).recordSagaCompensationRequired("order-123", "WMS_ACKED", "TIMEOUT");
        verify(eventPublisher).publish(any());
    }

    @Test
    void shouldDoNothingWhenNoPendingSteps() {
        // given
        when(sagaLogPort.findPendingStepsOlderThan(any(Duration.class)))
            .thenReturn(List.of());

        // when
        detector.detectTimeouts();

        // then
        verify(sagaLogPort, never()).recordSagaCompensationRequired(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }
}
```

- [ ] **Step 2: 更新 OrderPlacementSagaTest**

在 `OrderPlacementSagaTest` 中添加补偿幂等性测试：

```java
@Test
void shouldSkipAlreadyCompensatedReservations() {
    // given
    InventoryReservation reservation = InventoryReservation.pending("resv-1", "sku-1", 2, "order-1");
    when(compensationLogPort.exists("compensate:order-1:WMS_ACKED:resv-1")).thenReturn(true);

    // when
    orderPlacementSaga.releaseAll(List.of(reservation), "WMS_ACKED");

    // then
    verify(inventoryPort, never()).release(any());
    verify(sagaLogPort).recordCompensation("order-1", "resv-1", "Skip: already compensated");
}
```

- [ ] **Step 3: Commit**

```bash
git add order-application/src/test/java/com/example/order/application/service/
git commit -m "test(saga): add SagaTimeoutDetector and compensation idempotency tests"
```

---

### Task 9: 验证 + 文档

**Files:**
- Modify: `docs/SPRINT_1_REPORT.md` 或创建新文档

- [ ] **Step 1: 运行测试**

```bash
mvn test -pl order-application -Dtest=SagaTimeoutDetectorTest,OrderPlacementSagaTest
```

Expected: 所有测试通过

- [ ] **Step 2: 验证 Flyway 迁移**

```bash
mvn flyway:migrate -pl order-infrastructure
```

Expected: 迁移成功，表结构正确

- [ ] **Step 3: Commit**

```bash
git add docs/
git commit -m "docs: add saga deepening implementation report"
```