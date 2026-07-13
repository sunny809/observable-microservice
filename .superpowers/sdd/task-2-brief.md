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

