# Data-Intensive Patterns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three data-intensive design patterns to order-demo: Data Lifecycle Management (archive + cleanup for growing tables), JSON Native Storage + Query (replace TEXT blob with JSONB + JPA AttributeConverter), and Order Snapshots + Point-in-Time Query (time-travel state queries).

**Architecture:** All changes stay within the existing hexagonal architecture (application → adapter → infrastructure). Lifecycle management adds a @Scheduled component in the adapter layer; JSON native storage replaces manual serialization in OrderPersistenceAdapter with JPA AttributeConverters; snapshots add a new table + port + adapter. No new modules.

**Tech Stack:** Spring Boot 3.4.3, JPA AttributeConverter, PostgreSQL JSONB / H2 TEXT, @Scheduled, Flyway, Micrometer

## Global Constraints

- Package name: `com.order.demo`
- All existing tests must continue to pass after each task
- ArchUnit rules must remain satisfied: application layer must not depend on adapter/infrastructure; entities must reside in `..adapter.outbound.persistence..` or `..adapter.outbound.outbox..` or `..adapter.outbound.query..`; ports must be interfaces
- Flyway migrations are sequential: V10, V11, V12 (V1–V9 already exist)
- H2 compatibility required for tests — JPA AttributeConverter works with TEXT columns in H2; JSONB-specific SQL only runs on PostgreSQL (use `@Query(nativeQuery=true)` with conditional execution or separate query methods)
- `@EntityScan` and `@EnableJpaRepositories` already scan `com.order.demo.adapter.outbound` — new entities in `..persistence..` or `..outbox..` or `..query..` are auto-discovered
- `@EnableScheduling` is already on `OrderServiceApplication`
- `DataLifecycleManager` must use `@ConditionalOnProperty` so it can be disabled in test profiles (like OutboxPoller uses `@ConditionalOnBean`)
- Snapshot entity must reside in `..adapter.outbound.snapshot..` — ArchUnit rule needs updating to include this package

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `order-infrastructure/src/main/resources/db/migration/V10__create_saga_logs_archive.sql` | Archive table DDL |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManager.java` | @Scheduled archive + cleanup |
| `order-adapter/src/test/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManagerTest.java` | Unit test for lifecycle manager |
| `order-infrastructure/src/main/resources/db/migration/V11__alter_items_to_jsonb.sql` | TEXT → JSONB migration |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderItemListConverter.java` | JPA AttributeConverter for List\<OrderItem\> |
| `order-adapter/src/test/java/com/order/demo/adapter/outbound/persistence/OrderItemListConverterTest.java` | Unit test for converter |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/StringListConverter.java` | JPA AttributeConverter for List\<String\> |
| `order-adapter/src/test/java/com/order/demo/adapter/outbound/persistence/StringListConverterTest.java` | Unit test for converter |
| `order-infrastructure/src/main/resources/db/migration/V12__create_order_snapshots.sql` | Snapshot table DDL |
| `order-application/src/main/java/com/order/demo/application/port/out/OrderSnapshotPort.java` | Port interface for snapshots |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotEntity.java` | JPA entity for order_snapshots |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotJpaRepository.java` | JPA repository for snapshots |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotPersistenceAdapter.java` | Implements OrderSnapshotPort |
| `order-adapter/src/test/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotPersistenceAdapterTest.java` | Unit test for snapshot adapter |

### Modified Files

| File | Change |
|------|--------|
| `order-application/src/main/java/com/order/demo/application/port/out/SagaLogPort.java` | Add `archiveCompletedOlderThan(LocalDateTime)` method |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/SagaLogJpaRepository.java` | Add archive + delete query methods |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/SagaLogPersistenceAdapter.java` | Implement `archiveCompletedOlderThan()` |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/outbox/OutboxEventJpaRepository.java` | Add `deleteByStatusAndSentAtBefore()` |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/CompensationLogJpaRepository.java` | Add `deleteByStatusAndAttemptedAtBefore()` |
| `order-application/src/main/java/com/order/demo/application/port/out/MetricsPort.java` | Add `recordLifecycleArchived(int)` + `recordLifecycleDeleted(int)` |
| `order-adapter/src/main/java/com/order/demo/adapter/metrics/OrderMetrics.java` | Implement lifecycle metrics |
| `order-infrastructure/src/main/resources/application.yml` | Add `app.lifecycle.archive-cron` config |
| `order-adapter/src/test/resources/application.yml` | Disable lifecycle in tests |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderEntity.java` | Replace `String items` → `List<OrderItem> items` with `@Convert`; replace `String reservationIds` → `List<String> reservationIds` with `@Convert` |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderPersistenceAdapter.java` | Delete ~60 lines of manual serialization; simplify toEntity/toDomain |
| `order-application/src/main/java/com/order/demo/application/port/out/OrderRepositoryPort.java` | Add `findBySku(String sku)` |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderJpaRepository.java` | Add native JSON query method |
| `order-adapter/src/main/java/com/order/demo/adapter/inbound/rest/OrderController.java` | Add `GET /api/v1/orders/by-sku?sku=...` endpoint |
| `order-application/src/main/java/com/order/demo/application/service/OrderPlacementSaga.java` | Add `orderSnapshotPort.saveSnapshot()` calls after each state transition |
| `order-application/src/main/java/com/order/demo/application/port/out/OrderQueryPort.java` | Add `findOrderAt(String orderId, Instant pointInTime)` |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/query/OrderQueryAdapter.java` | Implement `findOrderAt()` |
| `order-adapter/src/main/java/com/order/demo/adapter/inbound/rest/OrderController.java` | Add `GET /api/v1/orders/{orderId}/at?time=...` endpoint |
| `order-adapter/src/main/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManager.java` | Add snapshot archive logic (Task 10) |
| `order-infrastructure/src/test/java/com/order/demo/infrastructure/ArchitectureTest.java` | Add `..adapter.outbound.snapshot..` and `..adapter.outbound.lifecycle..` to entity rule |

---

### Task 1: Lifecycle — Flyway V10 + SagaLogPort Archive Method

**Files:**
- Create: `order-infrastructure/src/main/resources/db/migration/V10__create_saga_logs_archive.sql`
- Modify: `order-application/src/main/java/com/order/demo/application/port/out/SagaLogPort.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/SagaLogJpaRepository.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/SagaLogPersistenceAdapter.java`

**Interfaces:**
- Consumes: existing `SagaLogPort`, `SagaLogJpaRepository`, `SagaLogPersistenceAdapter`
- Produces: `SagaLogPort.archiveCompletedOlderThan(LocalDateTime threshold)` returning `int` (count of archived rows); `SagaLogJpaRepository` gains `@Modifying` queries for archive INSERT + DELETE

- [ ] **Step 1: Write the Flyway migration V10**

```sql
-- V10__create_saga_logs_archive.sql
-- Archive table for completed saga logs older than 30 days
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

- [ ] **Step 2: Add `archiveCompletedOlderThan` to SagaLogPort**

Add this method to `order-application/src/main/java/com/order/demo/application/port/out/SagaLogPort.java`:

```java
/**
 * Archives completed saga logs older than the given threshold.
 * Moves matching rows from saga_logs to saga_logs_archive, then deletes them.
 *
 * @param threshold only rows with step_status IN ('COMPLETED','FAILED','COMPENSATED')
 *                  AND completed_at before this threshold are archived
 * @return the number of rows archived
 */
int archiveCompletedOlderThan(LocalDateTime threshold);
```

- [ ] **Step 3: Add archive queries to SagaLogJpaRepository**

Add these methods to `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/SagaLogJpaRepository.java`:

```java
@Modifying
@Query(value = "INSERT INTO saga_logs_archive " +
       "SELECT id, order_id, step, detail, created_at, saga_type, step_name, step_status, " +
       "started_at, completed_at, compensation_status, retry_count, next_retry_at, " +
       "previous_status, new_status, changed_by " +
       "FROM saga_logs " +
       "WHERE step_status IN ('COMPLETED','FAILED','COMPENSATED') AND completed_at < :threshold",
       nativeQuery = true)
int archiveCompletedOlderThan(@Param("threshold") LocalDateTime threshold);

@Modifying
@Query(value = "DELETE FROM saga_logs " +
       "WHERE step_status IN ('COMPLETED','FAILED','COMPENSATED') AND completed_at < :threshold",
       nativeQuery = true)
int deleteArchivedOlderThan(@Param("threshold") LocalDateTime threshold);
```

- [ ] **Step 4: Implement `archiveCompletedOlderThan` in SagaLogPersistenceAdapter**

Add this method to `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/SagaLogPersistenceAdapter.java`:

```java
@Override
@Transactional
public int archiveCompletedOlderThan(LocalDateTime threshold) {
    int archived = repository.archiveCompletedOlderThan(threshold);
    if (archived > 0) {
        repository.deleteArchivedOlderThan(threshold);
    }
    return archived;
}
```

- [ ] **Step 5: Run tests to verify nothing is broken**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -pl order-application,order-adapter -q`
Expected: All existing tests pass

- [ ] **Step 6: Commit**

```bash
git add order-infrastructure/src/main/resources/db/migration/V10__create_saga_logs_archive.sql \
        order-application/src/main/java/com/order/demo/application/port/out/SagaLogPort.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/SagaLogJpaRepository.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/SagaLogPersistenceAdapter.java
git commit -m "feat: add saga_logs_archive table and archive port method (lifecycle step 1)"
```

---

### Task 2: Lifecycle — Outbox + Compensation Cleanup Methods

**Files:**
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/outbox/OutboxEventJpaRepository.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/CompensationLogJpaRepository.java`

**Interfaces:**
- Consumes: existing `OutboxEventJpaRepository`, `CompensationLogJpaRepository`
- Produces: `OutboxEventJpaRepository.deleteByStatusAndSentAtBefore(String status, LocalDateTime threshold)` returning `int`; `CompensationLogJpaRepository.deleteByStatusAndAttemptedAtBefore(String status, LocalDateTime threshold)` returning `int`

- [ ] **Step 1: Add cleanup method to OutboxEventJpaRepository**

Add to `order-adapter/src/main/java/com/order/demo/adapter/outbound/outbox/OutboxEventJpaRepository.java`:

```java
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Modifying
@Query("DELETE FROM OutboxEventEntity e WHERE e.status = :status AND e.sentAt < :threshold")
int deleteByStatusAndSentAtBefore(@Param("status") String status,
                                  @Param("threshold") LocalDateTime threshold);
```

- [ ] **Step 2: Add cleanup method to CompensationLogJpaRepository**

Add to `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/CompensationLogJpaRepository.java`:

```java
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Modifying
@Query("DELETE FROM CompensationLogEntity c WHERE c.status = :status AND c.attemptedAt < :threshold")
int deleteByStatusAndAttemptedAtBefore(@Param("status") String status,
                                       @Param("threshold") LocalDateTime threshold);
```

- [ ] **Step 3: Run tests to verify**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -pl order-adapter -q`
Expected: All existing tests pass

- [ ] **Step 4: Commit**

```bash
git add order-adapter/src/main/java/com/order/demo/adapter/outbound/outbox/OutboxEventJpaRepository.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/CompensationLogJpaRepository.java
git commit -m "feat: add outbox and compensation cleanup query methods (lifecycle step 2)"
```

---

### Task 3: Lifecycle — MetricsPort + OrderMetrics Lifecycle Metrics

**Files:**
- Modify: `order-application/src/main/java/com/order/demo/application/port/out/MetricsPort.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/metrics/OrderMetrics.java`

**Interfaces:**
- Consumes: existing `MetricsPort`, `OrderMetrics`
- Produces: `MetricsPort.recordLifecycleArchived(int count)` + `MetricsPort.recordLifecycleDeleted(int count)`

- [ ] **Step 1: Add lifecycle methods to MetricsPort**

Add to `order-application/src/main/java/com/order/demo/application/port/out/MetricsPort.java`:

```java
/**
 * Records the number of rows archived by the data lifecycle manager.
 *
 * @param count the number of rows archived
 */
void recordLifecycleArchived(int count);

/**
 * Records the number of rows deleted by the data lifecycle manager.
 *
 * @param count the number of rows deleted
 */
void recordLifecycleDeleted(int count);
```

- [ ] **Step 2: Implement lifecycle metrics in OrderMetrics**

Add to `order-adapter/src/main/java/com/order/demo/adapter/metrics/OrderMetrics.java`:

Add constants:
```java
private static final String METRIC_LIFECYCLE_ARCHIVED = "lifecycle.rows.archived";
private static final String METRIC_LIFECYCLE_DELETED = "lifecycle.rows.deleted";
```

Add methods:
```java
@Override
public void recordLifecycleArchived(int count) {
    registry.counter(METRIC_LIFECYCLE_ARCHIVED).increment(count);
}

@Override
public void recordLifecycleDeleted(int count) {
    registry.counter(METRIC_LIFECYCLE_DELETED).increment(count);
}
```

- [ ] **Step 3: Run tests to verify**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -pl order-application,order-adapter -q`
Expected: All existing tests pass

- [ ] **Step 4: Commit**

```bash
git add order-application/src/main/java/com/order/demo/application/port/out/MetricsPort.java \
        order-adapter/src/main/java/com/order/demo/adapter/metrics/OrderMetrics.java
git commit -m "feat: add lifecycle archive/delete metrics (lifecycle step 3)"
```

---

### Task 4: Lifecycle — DataLifecycleManager + Config + Test

**Files:**
- Create: `order-adapter/src/main/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManager.java`
- Create: `order-adapter/src/test/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManagerTest.java`
- Modify: `order-infrastructure/src/main/resources/application.yml`
- Modify: `order-adapter/src/test/resources/application.yml`

**Interfaces:**
- Consumes: `SagaLogPort.archiveCompletedOlderThan(LocalDateTime)` (Task 1), `OutboxEventJpaRepository.deleteByStatusAndSentAtBefore(String, LocalDateTime)` (Task 2), `CompensationLogJpaRepository.deleteByStatusAndAttemptedAtBefore(String, LocalDateTime)` (Task 2), `MetricsPort.recordLifecycleArchived(int)` + `recordLifecycleDeleted(int)` (Task 3)
- Produces: `DataLifecycleManager` component with `@Scheduled archiveAndCleanup()` method

- [ ] **Step 1: Create DataLifecycleManager**

Create `order-adapter/src/main/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManager.java`:

```java
package com.order.demo.adapter.outbound.lifecycle;

import com.order.demo.adapter.outbound.outbox.OutboxEventJpaRepository;
import com.order.demo.adapter.outbound.persistence.CompensationLogJpaRepository;
import com.order.demo.application.port.out.MetricsPort;
import com.order.demo.application.port.out.SagaLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled component that archives old saga logs and deletes
 * sent outbox events and completed compensation logs.
 *
 * <p>Active only when {@code app.lifecycle.enabled=true} (default: true).
 * Disabled in test profiles to avoid interference.
 */
@Component
@ConditionalOnProperty(name = "app.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
public class DataLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(DataLifecycleManager.class);

    private final SagaLogPort sagaLogPort;
    private final OutboxEventJpaRepository outboxRepo;
    private final CompensationLogJpaRepository compensationRepo;
    private final MetricsPort metricsPort;

    public DataLifecycleManager(SagaLogPort sagaLogPort,
                                OutboxEventJpaRepository outboxRepo,
                                private final CompensationLogJpaRepository compensationRepo,
                                MetricsPort metricsPort) {
        this.sagaLogPort = sagaLogPort;
        this.outboxRepo = outboxRepo;
        this.compensationRepo = compensationRepo;
        this.metricsPort = metricsPort;
    }

    @Scheduled(cron = "${app.lifecycle.archive-cron:0 0 3 * * ?}")
    @Transactional
    public void archiveAndCleanup() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        int archived = sagaLogPort.archiveCompletedOlderThan(thirtyDaysAgo);
        int outboxDeleted = outboxRepo.deleteByStatusAndSentAtBefore("SENT", sevenDaysAgo);
        int compensationDeleted = compensationRepo.deleteByStatusAndAttemptedAtBefore("COMPLETED", thirtyDaysAgo);

        metricsPort.recordLifecycleArchived(archived);
        metricsPort.recordLifecycleDeleted(outboxDeleted + compensationDeleted);

        log.info("Lifecycle cleanup: archived={} saga logs, deleted={} outbox events, deleted={} compensation logs",
                archived, outboxDeleted, compensationDeleted);
    }
}
```

**IMPORTANT:** The constructor parameter `private final` is a typo — it should be just `CompensationLogJpaRepository compensationRepo`. Fix this in the actual code.

- [ ] **Step 2: Add lifecycle config to application.yml**

Add to `order-infrastructure/src/main/resources/application.yml` under the `app:` section:

```yaml
  lifecycle:
    enabled: true
    archive-cron: "0 0 3 * * ?"
```

- [ ] **Step 3: Disable lifecycle in test application.yml**

Add to `order-adapter/src/test/resources/application.yml`:

```yaml
app:
  lifecycle:
    enabled: false
```

- [ ] **Step 4: Write DataLifecycleManagerTest**

Create `order-adapter/src/test/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManagerTest.java`:

```java
package com.order.demo.adapter.outbound.lifecycle;

import com.order.demo.adapter.outbound.outbox.OutboxEventJpaRepository;
import com.order.demo.adapter.outbound.persistence.CompensationLogJpaRepository;
import com.order.demo.application.port.out.MetricsPort;
import com.order.demo.application.port.out.SagaLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class DataLifecycleManagerTest {

    @Mock private SagaLogPort sagaLogPort;
    @Mock private OutboxEventJpaRepository outboxRepo;
    @Mock private CompensationLogJpaRepository compensationRepo;
    @Mock private MetricsPort metricsPort;

    private DataLifecycleManager manager;

    @BeforeEach
    void setUp() {
        manager = new DataLifecycleManager(sagaLogPort, outboxRepo, compensationRepo, metricsPort);
    }

    @Test
    @DisplayName("archiveAndCleanup archives saga logs and deletes outbox/compensation records")
    void archiveAndCleanup_archivesAndDeletes() {
        when(sagaLogPort.archiveCompletedOlderThan(any())).thenReturn(50);
        when(outboxRepo.deleteByStatusAndSentAtBefore(eq("SENT"), any())).thenReturn(30);
        when(compensationRepo.deleteByStatusAndAttemptedAtBefore(eq("COMPLETED"), any())).thenReturn(10);

        manager.archiveAndCleanup();

        verify(sagaLogPort).archiveCompletedOlderThan(any());
        verify(outboxRepo).deleteByStatusAndSentAtBefore(eq("SENT"), any());
        verify(compensationRepo).deleteByStatusAndAttemptedAtBefore(eq("COMPLETED"), any());
        verify(metricsPort).recordLifecycleArchived(50);
        verify(metricsPort).recordLifecycleDeleted(40);
    }

    @Test
    @DisplayName("archiveAndCleanup with zero rows still records metrics")
    void archiveAndCleanup_zeroRows_recordsZeroMetrics() {
        when(sagaLogPort.archiveCompletedOlderThan(any())).thenReturn(0);
        when(outboxRepo.deleteByStatusAndSentAtBefore(eq("SENT"), any())).thenReturn(0);
        when(compensationRepo.deleteByStatusAndAttemptedAtBefore(eq("COMPLETED"), any())).thenReturn(0);

        manager.archiveAndCleanup();

        verify(metricsPort).recordLifecycleArchived(0);
        verify(metricsPort).recordLifecycleDeleted(0);
    }
}
```

- [ ] **Step 5: Run tests to verify**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -pl order-adapter -q`
Expected: All tests pass including new DataLifecycleManagerTest

- [ ] **Step 6: Commit**

```bash
git add order-adapter/src/main/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManager.java \
        order-adapter/src/test/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManagerTest.java \
        order-infrastructure/src/main/resources/application.yml \
        order-adapter/src/test/resources/application.yml
git commit -m "feat: add DataLifecycleManager with scheduled archive and cleanup (lifecycle step 4)"
```

---

### Task 5: JSON Native — Flyway V11 + JPA AttributeConverters

**Files:**
- Create: `order-infrastructure/src/main/resources/db/migration/V11__alter_items_to_jsonb.sql`
- Create: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderItemListConverter.java`
- Create: `order-adapter/src/test/java/com/order/demo/adapter/outbound/persistence/OrderItemListConverterTest.java`
- Create: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/StringListConverter.java`
- Create: `order-adapter/src/test/java/com/order/demo/adapter/outbound/persistence/StringListConverterTest.java`

**Interfaces:**
- Consumes: `OrderItem` record from `order-application`
- Produces: `OrderItemListConverter` (converts `List<OrderItem>` ↔ `String` JSON), `StringListConverter` (converts `List<String>` ↔ `String` JSON)

- [ ] **Step 1: Write Flyway V11 migration**

Create `order-infrastructure/src/main/resources/db/migration/V11__alter_items_to_jsonb.sql`:

```sql
-- V11__alter_items_to_jsonb.sql
-- Convert items and reservation_ids from TEXT to JSONB (PostgreSQL)
-- H2 uses TEXT columns — JPA AttributeConverter handles serialization regardless of column type

-- For PostgreSQL: ALTER TABLE orders ALTER COLUMN items SET DATA TYPE JSONB USING items::JSONB;
-- For PostgreSQL: ALTER TABLE orders ALTER COLUMN reservation_ids SET DATA TYPE JSONB USING reservation_ids::JSONB;
-- H2 does not support JSONB, so we keep TEXT columns and rely on @Convert for type safety.
-- The migration is a no-op for H2; PostgreSQL-specific migrations can use Flyway's
-- db/migration/postgresql/ directory in production.

-- This migration is intentionally empty for H2 compatibility.
-- In production, use Flyway's vendor-specific migration paths:
--   db/migration/common/V11__alter_items_to_jsonb.sql (empty or H2-safe)
--   db/migration/postgresql/V11__alter_items_to_jsonb.sql (actual JSONB ALTER)
-- For now, the JPA AttributeConverter provides type safety regardless of column type.
```

**Note:** Since the project uses H2 for tests and the `@Convert` annotation works with any column type (TEXT or JSONB), this migration is intentionally a no-op comment. In production, a PostgreSQL-specific migration would alter the column types. The `columnDefinition = "JSONB"` in the entity annotation is only used by Hibernate's DDL generation (which is disabled — `ddl-auto: validate`), not by Flyway.

- [ ] **Step 2: Create OrderItemListConverter**

Create `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderItemListConverter.java`:

```java
package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.port.in.OrderItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA AttributeConverter that serializes/deserializes {@code List<OrderItem>}
 * to/from JSON. Replaces the manual serialization previously in
 * {@link OrderPersistenceAdapter}.
 *
 * <p>Works with both TEXT columns (H2) and JSONB columns (PostgreSQL).
 * The database column type is irrelevant — this converter handles the
 * Java ↔ String mapping; the JDBC driver handles String ↔ column type.
 */
@Converter
public class OrderItemListConverter implements AttributeConverter<List<OrderItem>, String> {

    private static final Logger log = LoggerFactory.getLogger(OrderItemListConverter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<OrderItem> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order items to JSON", e);
        }
    }

    @Override
    public List<OrderItem> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<OrderItem>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize order items from JSON: {}", dbData, e);
            throw new IllegalStateException("Failed to deserialize order items — possible data corruption", e);
        }
    }
}
```

- [ ] **Step 3: Write OrderItemListConverterTest**

Create `order-adapter/src/test/java/com/order/demo/adapter/outbound/persistence/OrderItemListConverterTest.java`:

```java
package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.port.in.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderItemListConverterTest {

    private OrderItemListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new OrderItemListConverter();
    }

    @Test
    @DisplayName("convertToDatabaseColumn serializes items to JSON")
    void toDatabaseColumn_serializesItems() {
        List<OrderItem> items = List.of(new OrderItem("SKU-1", 3), new OrderItem("SKU-2", 1));
        String json = converter.convertToDatabaseColumn(items);
        assertNotNull(json);
        assertTrue(json.contains("SKU-1"));
        assertTrue(json.contains("SKU-2"));
    }

    @Test
    @DisplayName("convertToDatabaseColumn returns [] for null")
    void toDatabaseColumn_null_returnsEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("convertToDatabaseColumn returns [] for empty list")
    void toDatabaseColumn_emptyList_returnsEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(Collections.emptyList()));
    }

    @Test
    @DisplayName("convertToEntityAttribute deserializes JSON to items")
    void toEntityAttribute_deserializesJson() {
        String json = "[{\"sku\":\"SKU-1\",\"quantity\":3}]";
        List<OrderItem> items = converter.convertToEntityAttribute(json);
        assertEquals(1, items.size());
        assertEquals("SKU-1", items.get(0).sku());
        assertEquals(3, items.get(0).quantity());
    }

    @Test
    @DisplayName("convertToEntityAttribute returns empty list for null")
    void toEntityAttribute_null_returnsEmptyList() {
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("convertToEntityAttribute returns empty list for blank string")
    void toEntityAttribute_blank_returnsEmptyList() {
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute("   "));
    }

    @Test
    @DisplayName("round-trip: serialize then deserialize preserves data")
    void roundTrip_preservesData() {
        List<OrderItem> original = List.of(new OrderItem("SKU-A", 10));
        String json = converter.convertToDatabaseColumn(original);
        List<OrderItem> restored = converter.convertToEntityAttribute(json);
        assertEquals(original, restored);
    }
}
```

- [ ] **Step 4: Create StringListConverter**

Create `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/StringListConverter.java`:

```java
package com.order.demo.adapter.outbound.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA AttributeConverter that serializes/deserializes {@code List<String>}
 * to/from JSON. Replaces the manual serialization previously in
 * {@link OrderPersistenceAdapter}.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final Logger log = LoggerFactory.getLogger(StringListConverter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize string list to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize string list from JSON: {}", dbData, e);
            throw new IllegalStateException("Failed to deserialize string list", e);
        }
    }
}
```

- [ ] **Step 5: Write StringListConverterTest**

Create `order-adapter/src/test/java/com/order/demo/adapter/outbound/persistence/StringListConverterTest.java`:

```java
package com.order.demo.adapter.outbound.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class StringListConverterTest {

    private StringListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringListConverter();
    }

    @Test
    @DisplayName("convertToDatabaseColumn serializes list to JSON")
    void toDatabaseColumn_serializesList() {
        List<String> ids = List.of("res-1", "res-2");
        String json = converter.convertToDatabaseColumn(ids);
        assertEquals("[\"res-1\",\"res-2\"]", json);
    }

    @Test
    @DisplayName("convertToDatabaseColumn returns [] for null")
    void toDatabaseColumn_null_returnsEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("convertToEntityAttribute deserializes JSON to list")
    void toEntityAttribute_deserializesJson() {
        String json = "[\"res-1\",\"res-2\"]";
        List<String> ids = converter.convertToEntityAttribute(json);
        assertEquals(List.of("res-1", "res-2"), ids);
    }

    @Test
    @DisplayName("convertToEntityAttribute returns empty list for null")
    void toEntityAttribute_null_returnsEmptyList() {
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("round-trip preserves data")
    void roundTrip_preservesData() {
        List<String> original = List.of("abc", "def");
        String json = converter.convertToDatabaseColumn(original);
        List<String> restored = converter.convertToEntityAttribute(json);
        assertEquals(original, restored);
    }
}
```

- [ ] **Step 6: Run converter tests**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -pl order-adapter -Dtest="OrderItemListConverterTest,StringListConverterTest" -q`
Expected: All 12 converter tests pass

- [ ] **Step 7: Commit**

```bash
git add order-infrastructure/src/main/resources/db/migration/V11__alter_items_to_jsonb.sql \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderItemListConverter.java \
        order-adapter/src/test/java/com/order/demo/adapter/outbound/persistence/OrderItemListConverterTest.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/StringListConverter.java \
        order-adapter/src/test/java/com/order/demo/adapter/outbound/persistence/StringListConverterTest.java
git commit -m "feat: add JPA AttributeConverters for JSON columns (JSON native step 1)"
```

---

### Task 6: JSON Native — Refactor OrderEntity + OrderPersistenceAdapter

**Files:**
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderEntity.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderPersistenceAdapter.java`

**Interfaces:**
- Consumes: `OrderItemListConverter`, `StringListConverter` (Task 5)
- Produces: `OrderEntity` with typed `List<OrderItem> items` and `List<String> reservationIds` fields; `OrderPersistenceAdapter` with simplified toEntity/toDomain (no manual serialization)

- [ ] **Step 1: Refactor OrderEntity to use @Convert**

Replace the `items` and `reservationIds` fields in `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderEntity.java`:

Remove:
```java
import jakarta.persistence.Lob;

@Lob
@Column(name = "items", columnDefinition = "TEXT")
private String items;

@Lob
@Column(name = "reservation_ids", columnDefinition = "TEXT")
private String reservationIds;
```

Add:
```java
import com.order.demo.application.port.in.OrderItem;
import java.util.List;

@Convert(converter = OrderItemListConverter.class)
@Column(name = "items", columnDefinition = "TEXT")
private List<OrderItem> items;

@Convert(converter = StringListConverter.class)
@Column(name = "reservation_ids", columnDefinition = "TEXT")
private List<String> reservationIds;
```

Update getters/setters:
```java
public List<OrderItem> getItems() { return items; }
public void setItems(List<OrderItem> items) { this.items = items; }
public List<String> getReservationIds() { return reservationIds; }
public void setReservationIds(List<String> reservationIds) { this.reservationIds = reservationIds; }
```

- [ ] **Step 2: Simplify OrderPersistenceAdapter**

In `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderPersistenceAdapter.java`:

**Delete** these methods entirely (approximately 60 lines):
- `serializeItems(List<OrderItem>)`
- `deserializeItems(String)`
- `serializeReservationIds(List<String>)`
- `deserializeReservationIds(String)`
- `createOrderItem(String, int)` (the @JsonCreator private method)
- `OrderItemMixin` inner class

**Remove** the `ObjectMapper` field and its initialization in the constructor.

**Simplify** `toEntity`:
```java
private OrderEntity toEntity(Order order) {
    OrderEntity entity = new OrderEntity(
            order.getOrderId(),
            order.getCustomerId(),
            order.getIdempotencyKey(),
            order.getReservationId(),
            order.getStatus().name(),
            LocalDateTime.ofInstant(order.getCreatedAt(), java.time.ZoneOffset.UTC));
    entity.setItems(order.getItems());
    entity.setReservationIds(order.getAllReservationIds());
    if (order.getVersion() != null) {
        entity.setVersion(order.getVersion());
    }
    return entity;
}
```

**Simplify** `toDomain`:
```java
private Order toDomain(OrderEntity entity) {
    return new Order(
            entity.getId(),
            entity.getCustomerId(),
            entity.getItems(),
            OrderStatus.valueOf(entity.getStatus()),
            entity.getIdempotencyKey(),
            entity.getReservationId(),
            entity.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant(),
            entity.getReservationIds(),
            entity.getVersion());
}
```

**Remove** unused imports: `ObjectMapper`, `TypeReference`, `JsonCreator`, `JsonProperty`, `Collections`.

- [ ] **Step 3: Run all tests to verify refactoring**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -pl order-adapter -q`
Expected: All existing tests pass (the converters handle serialization transparently)

- [ ] **Step 4: Commit**

```bash
git add order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderEntity.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderPersistenceAdapter.java
git commit -m "refactor: replace manual JSON serialization with JPA @Convert (JSON native step 2)"
```

---

### Task 7: JSON Native — findBySku Query + Controller Endpoint

**Files:**
- Modify: `order-application/src/main/java/com/order/demo/application/port/out/OrderRepositoryPort.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderJpaRepository.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderPersistenceAdapter.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/inbound/rest/OrderController.java`

**Interfaces:**
- Consumes: `OrderRepositoryPort`, `OrderJpaRepository`, `OrderPersistenceAdapter`, `OrderController`
- Produces: `OrderRepositoryPort.findBySku(String sku)` returning `List<Order>`; `GET /api/v1/orders/by-sku?sku=...` endpoint

- [ ] **Step 1: Add findBySku to OrderRepositoryPort**

Add to `order-application/src/main/java/com/order/demo/application/port/out/OrderRepositoryPort.java`:

```java
/**
 * Finds all orders containing an item with the given SKU.
 *
 * @param sku the SKU to search for
 * @return list of orders containing the SKU
 */
List<Order> findBySku(String sku);
```

- [ ] **Step 2: Add native JSON query to OrderJpaRepository**

Add to `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderJpaRepository.java`:

```java
/**
 * Finds orders containing an item with the given SKU using PostgreSQL JSONB containment operator.
 * Falls back to LIKE for H2 compatibility in tests.
 */
@Query(value = "SELECT * FROM orders WHERE items LIKE :skuPattern", nativeQuery = true)
List<OrderEntity> findByItemsContainingSku(@Param("skuPattern") String skuPattern);
```

**Note:** Using `LIKE` instead of PostgreSQL's `@>` operator for H2 compatibility. The `skuPattern` is formatted as `%"sku":"SKU-1"%` by the adapter. In production with PostgreSQL, this can be upgraded to `items @> :skuFilter::jsonb` for index-backed queries.

- [ ] **Step 3: Implement findBySku in OrderPersistenceAdapter**

Add to `order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderPersistenceAdapter.java`:

```java
@Override
public List<Order> findBySku(String sku) {
    String skuPattern = "%\"sku\":\"" + sku + "\"%";
    return repository.findByItemsContainingSku(skuPattern).stream()
            .map(this::toDomain)
            .toList();
}
```

- [ ] **Step 4: Add by-sku endpoint to OrderController**

Add to `order-adapter/src/main/java/com/order/demo/adapter/inbound/rest/OrderController.java`:

```java
/**
 * Finds orders containing a specific SKU.
 */
@GetMapping("/by-sku")
@Operation(summary = "Find orders by SKU", description = "Finds all orders containing an item with the given SKU")
public List<OrderSummary> findBySku(@RequestParam String sku) {
    return placeOrderUseCase.findBySku(sku).stream()
            .map(this::toSummary)
            .toList();
}
```

This requires adding a `findBySku` method to `PlaceOrderUseCase` (or using `OrderRepositoryPort` directly). Since the controller already has `PlaceOrderUseCase`, the cleanest approach is to add `findBySku` to `PlaceOrderUseCase`:

Add to `order-application/src/main/java/com/order/demo/application/port/in/PlaceOrderUseCase.java`:
```java
List<Order> findBySku(String sku);
```

And implement in `OrderPlacementSaga` (which implements `PlaceOrderUseCase`):
```java
@Override
public List<Order> findBySku(String sku) {
    return orderRepository.findBySku(sku);
}
```

Also add a `toSummary` helper to `OrderController`:
```java
private OrderSummary toSummary(Order order) {
    return new OrderSummary(
            order.getOrderId(),
            order.getCustomerId(),
            order.getStatus().name(),
            order.getCreatedAt(),
            order.getAllReservationIds().size(),
            order.getItems().stream().mapToInt(OrderItem::quantity).sum(),
            null, null
    );
}
```

- [ ] **Step 5: Run tests to verify**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -q`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add order-application/src/main/java/com/order/demo/application/port/out/OrderRepositoryPort.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderJpaRepository.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/persistence/OrderPersistenceAdapter.java \
        order-application/src/main/java/com/order/demo/application/port/in/PlaceOrderUseCase.java \
        order-application/src/main/java/com/order/demo/application/service/OrderPlacementSaga.java \
        order-adapter/src/main/java/com/order/demo/adapter/inbound/rest/OrderController.java
git commit -m "feat: add findBySku query and /by-sku endpoint (JSON native step 3)"
```

---

### Task 8: Snapshots — Flyway V12 + OrderSnapshotPort + Entity + Repository

**Files:**
- Create: `order-infrastructure/src/main/resources/db/migration/V12__create_order_snapshots.sql`
- Create: `order-application/src/main/java/com/order/demo/application/port/out/OrderSnapshotPort.java`
- Create: `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotEntity.java`
- Create: `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotJpaRepository.java`

**Interfaces:**
- Consumes: `Order` domain object from `order-application`
- Produces: `OrderSnapshotPort.saveSnapshot(Order order, String reason)`, `OrderSnapshotPort.findSnapshotAt(String orderId, Instant pointInTime)` returning `Optional<OrderSummary>`; `OrderSnapshotEntity` + `OrderSnapshotJpaRepository`

- [ ] **Step 1: Write Flyway V12 migration**

Create `order-infrastructure/src/main/resources/db/migration/V12__create_order_snapshots.sql`:

```sql
-- V12__create_order_snapshots.sql
-- Order state snapshots for point-in-time queries
CREATE TABLE order_snapshots (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id    VARCHAR(36) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    snapshot    TEXT NOT NULL,
    version     BIGINT NOT NULL,
    reason      VARCHAR(100),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshots_order_id_created ON order_snapshots(order_id, created_at DESC);
```

**Note:** Using `GENERATED BY DEFAULT AS IDENTITY` which is H2-compatible. PostgreSQL also supports this syntax.

- [ ] **Step 2: Create OrderSnapshotPort**

Create `order-application/src/main/java/com/order/demo/application/port/out/OrderSnapshotPort.java`:

```java
package com.order.demo.application.port.out;

import com.order.demo.application.domain.Order;
import com.order.demo.application.port.in.OrderSummary;
import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for order state snapshots.
 *
 * <p>Enables point-in-time queries: "what was the state of order X at time T?"
 * Snapshots are taken after each significant state transition in the saga.
 */
public interface OrderSnapshotPort {

    /**
     * Saves a snapshot of the current order state.
     *
     * @param order  the order to snapshot
     * @param reason the trigger reason (e.g., "ORDER_CREATED", "WMS_ACKED")
     */
    void saveSnapshot(Order order, String reason);

    /**
     * Finds the order state at a specific point in time.
     *
     * @param orderId     the order ID
     * @param pointInTime the point in time
     * @return the order summary at that time, or empty if no snapshot exists
     */
    Optional<OrderSummary> findSnapshotAt(String orderId, Instant pointInTime);
}
```

- [ ] **Step 3: Create OrderSnapshotEntity**

Create `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotEntity.java`:

```java
package com.order.demo.adapter.outbound.snapshot;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_snapshots")
public class OrderSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "snapshot", nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public OrderSnapshotEntity() {}

    // Getters
    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getSnapshot() { return snapshot; }
    public Long getVersion() { return version; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setStatus(String status) { this.status = status; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public void setVersion(Long version) { this.version = version; }
    public void setReason(String reason) { this.reason = reason; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: Create OrderSnapshotJpaRepository**

Create `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotJpaRepository.java`:

```java
package com.order.demo.adapter.outbound.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderSnapshotJpaRepository extends JpaRepository<OrderSnapshotEntity, Long> {

    @Query(value = "SELECT * FROM order_snapshots " +
           "WHERE order_id = :orderId AND created_at <= :pointInTime " +
           "ORDER BY created_at DESC LIMIT 1",
           nativeQuery = true)
    Optional<OrderSnapshotEntity> findLatestSnapshotAt(
            @Param("orderId") String orderId,
            @Param("pointInTime") LocalDateTime pointInTime);
}
```

- [ ] **Step 5: Run tests to verify compilation**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn compile -q`
Expected: Compilation succeeds

- [ ] **Step 6: Commit**

```bash
git add order-infrastructure/src/main/resources/db/migration/V12__create_order_snapshots.sql \
        order-application/src/main/java/com/order/demo/application/port/out/OrderSnapshotPort.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotEntity.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotJpaRepository.java
git commit -m "feat: add order_snapshots table, port, entity, and repository (snapshots step 1)"
```

---

### Task 9: Snapshots — Persistence Adapter + Saga Integration + Query Endpoint

**Files:**
- Create: `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotPersistenceAdapter.java`
- Create: `order-adapter/src/test/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotPersistenceAdapterTest.java`
- Modify: `order-application/src/main/java/com/order/demo/application/service/OrderPlacementSaga.java`
- Modify: `order-application/src/main/java/com/order/demo/application/port/out/OrderQueryPort.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/query/OrderQueryAdapter.java`
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/inbound/rest/OrderController.java`
- Modify: `order-infrastructure/src/test/java/com/order/demo/infrastructure/ArchitectureTest.java`

**Interfaces:**
- Consumes: `OrderSnapshotPort` (Task 8), `OrderSnapshotJpaRepository` (Task 8), `OrderQueryPort`, `OrderQueryAdapter`, `OrderPlacementSaga`
- Produces: `OrderSnapshotPersistenceAdapter` implementing `OrderSnapshotPort`; saga integration with `saveSnapshot()` calls; `OrderQueryPort.findOrderAt(String, Instant)`; `GET /api/v1/orders/{orderId}/at?time=...` endpoint

- [ ] **Step 1: Create OrderSnapshotPersistenceAdapter**

Create `order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotPersistenceAdapter.java`:

```java
package com.order.demo.adapter.outbound.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.demo.application.domain.Order;
import com.order.demo.application.port.in.OrderSummary;
import com.order.demo.application.port.out.OrderSnapshotPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class OrderSnapshotPersistenceAdapter implements OrderSnapshotPort {

    private static final Logger log = LoggerFactory.getLogger(OrderSnapshotPersistenceAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OrderSnapshotJpaRepository repository;

    public OrderSnapshotPersistenceAdapter(OrderSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveSnapshot(Order order, String reason) {
        OrderSnapshotEntity entity = new OrderSnapshotEntity();
        entity.setOrderId(order.getOrderId());
        entity.setStatus(order.getStatus().name());
        entity.setSnapshot(serializeOrder(order));
        entity.setVersion(order.getVersion() != null ? order.getVersion() : 0L);
        entity.setReason(reason);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public Optional<OrderSummary> findSnapshotAt(String orderId, java.time.Instant pointInTime) {
        LocalDateTime ldt = pointInTime.atZone(ZoneOffset.UTC).toLocalDateTime();
        return repository.findLatestSnapshotAt(orderId, ldt)
                .map(entity -> new OrderSummary(
                        entity.getOrderId(),
                        null, // customerId not in snapshot
                        entity.getStatus(),
                        entity.getCreatedAt().atZone(ZoneOffset.UTC).toInstant(),
                        0, 0, // reservation/quantity not in snapshot
                        null, entity.getReason()
                ));
    }

    private String serializeOrder(Order order) {
        try {
            return MAPPER.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order snapshot for orderId={}", order.getOrderId(), e);
            return "{}";
        }
    }
}
```

- [ ] **Step 2: Write OrderSnapshotPersistenceAdapterTest**

Create `order-adapter/src/test/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotPersistenceAdapterTest.java`:

```java
package com.order.demo.adapter.outbound.snapshot;

import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.port.in.OrderItem;
import com.order.demo.application.port.in.OrderSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class OrderSnapshotPersistenceAdapterTest {

    @Mock
    private OrderSnapshotJpaRepository repository;

    private OrderSnapshotPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrderSnapshotPersistenceAdapter(repository);
    }

    @Test
    @DisplayName("saveSnapshot persists entity with correct fields")
    void saveSnapshot_persistsEntity() {
        Order order = new Order(
                "order-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.CREATED, "idem-1", null,
                Instant.now(), List.of(), 1L
        );

        adapter.saveSnapshot(order, "ORDER_CREATED");

        ArgumentCaptor<OrderSnapshotEntity> captor = ArgumentCaptor.forClass(OrderSnapshotEntity.class);
        verify(repository).save(captor.capture());

        OrderSnapshotEntity saved = captor.getValue();
        assertEquals("order-1", saved.getOrderId());
        assertEquals("CREATED", saved.getStatus());
        assertEquals("ORDER_CREATED", saved.getReason());
        assertEquals(1L, saved.getVersion());
        assertNotNull(saved.getSnapshot());
    }

    @Test
    @DisplayName("findSnapshotAt returns summary when snapshot exists")
    void findSnapshotAt_returnsSummary() {
        OrderSnapshotEntity entity = new OrderSnapshotEntity();
        entity.setOrderId("order-1");
        entity.setStatus("WMS_ACKED");
        entity.setReason("WMS_ACKED");
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 1, 15, 0));

        when(repository.findLatestSnapshotAt(eq("order-1"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(entity));

        Optional<OrderSummary> result = adapter.findSnapshotAt("order-1",
                Instant.parse("2026-08-01T16:00:00Z"));

        assertTrue(result.isPresent());
        assertEquals("WMS_ACKED", result.get().status());
    }

    @Test
    @DisplayName("findSnapshotAt returns empty when no snapshot exists")
    void findSnapshotAt_returnsEmpty() {
        when(repository.findLatestSnapshotAt(eq("order-1"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        Optional<OrderSummary> result = adapter.findSnapshotAt("order-1",
                Instant.parse("2026-08-01T16:00:00Z"));

        assertTrue(result.isEmpty());
    }
}
```

- [ ] **Step 3: Integrate snapshot saving into OrderPlacementSaga**

Add `OrderSnapshotPort` as a dependency to `order-application/src/main/java/com/order/demo/application/service/OrderPlacementSaga.java`:

Add field:
```java
private final OrderSnapshotPort orderSnapshotPort;
```

Add to constructor parameter list.

Add `saveSnapshot` calls after each state transition in the saga's `@TransactionalEventListener` methods. For example, after the order is created:

```java
orderSnapshotPort.saveSnapshot(order, "ORDER_CREATED");
```

After WMS ack:
```java
orderSnapshotPort.saveSnapshot(order, "WMS_ACKED");
```

After WMS picking:
```java
orderSnapshotPort.saveSnapshot(order, "WMS_PICKED");
```

After TMS dispatch:
```java
orderSnapshotPort.saveSnapshot(order, "TMS_DISPATCHED");
```

**Important:** The `saveSnapshot` calls should be placed inside the existing `@TransactionalEventListener` handler methods, after the order status update, within the same transaction. This ensures the snapshot is only saved if the transaction commits.

- [ ] **Step 4: Add findOrderAt to OrderQueryPort**

Add to `order-application/src/main/java/com/order/demo/application/port/out/OrderQueryPort.java`:

```java
/**
 * Finds the order state at a specific point in time using snapshots.
 *
 * @param orderId     the order ID
 * @param pointInTime the point in time to query
 * @return the order summary at that time, or empty if no snapshot exists
 */
Optional<OrderSummary> findOrderAt(String orderId, Instant pointInTime);
```

Add the import: `import java.time.Instant;`

- [ ] **Step 5: Implement findOrderAt in OrderQueryAdapter**

Add to `order-adapter/src/main/java/com/order/demo/adapter/outbound/query/OrderQueryAdapter.java`:

Add `OrderSnapshotPort` as a dependency:

```java
private final OrderSnapshotPort orderSnapshotPort;
```

Update constructor:
```java
public OrderQueryAdapter(OrderViewJpaRepository viewRepository,
                         SagaLogPort sagaLogPort,
                         OrderSnapshotPort orderSnapshotPort) {
    this.viewRepository = viewRepository;
    this.sagaLogPort = sagaLogPort;
    this.orderSnapshotPort = orderSnapshotPort;
}
```

Add method:
```java
@Override
public Optional<OrderSummary> findOrderAt(String orderId, Instant pointInTime) {
    return orderSnapshotPort.findSnapshotAt(orderId, pointInTime);
}
```

- [ ] **Step 6: Add /at endpoint to OrderController**

Add to `order-adapter/src/main/java/com/order/demo/adapter/inbound/rest/OrderController.java`:

```java
/**
 * Retrieves order state at a specific point in time.
 */
@GetMapping("/{orderId}/at")
@Operation(summary = "Get order state at time",
           description = "Retrieves the order state at a specific point in time using snapshots")
public ResponseEntity<OrderSummary> getOrderAt(
        @PathVariable String orderId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant time) {
    return orderQueryPort.findOrderAt(orderId, time)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}
```

- [ ] **Step 7: Update ArchUnit entity rule**

In `order-infrastructure/src/test/java/com/order/demo/infrastructure/ArchitectureTest.java`, update the entity rule to include `..adapter.outbound.snapshot..`:

Change:
```java
static final ArchRule entity_classes_should_reside_in_adapter_outbound =
    classes().that().areAnnotatedWith(Entity.class)
        .should().resideInAnyPackage("..adapter.outbound.persistence..", "..adapter.outbound.outbox..", "..adapter.outbound.query..");
```

To:
```java
static final ArchRule entity_classes_should_reside_in_adapter_outbound =
    classes().that().areAnnotatedWith(Entity.class)
        .should().resideInAnyPackage("..adapter.outbound.persistence..", "..adapter.outbound.outbox..", "..adapter.outbound.query..", "..adapter.outbound.snapshot..");
```

- [ ] **Step 8: Run all tests**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -q`
Expected: All tests pass

- [ ] **Step 9: Commit**

```bash
git add order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotPersistenceAdapter.java \
        order-adapter/src/test/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotPersistenceAdapterTest.java \
        order-application/src/main/java/com/order/demo/application/service/OrderPlacementSaga.java \
        order-application/src/main/java/com/order/demo/application/port/out/OrderQueryPort.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/query/OrderQueryAdapter.java \
        order-adapter/src/main/java/com/order/demo/adapter/inbound/rest/OrderController.java \
        order-infrastructure/src/test/java/com/order/demo/infrastructure/ArchitectureTest.java
git commit -m "feat: add snapshot adapter, saga integration, and /at endpoint (snapshots step 2)"
```

---

### Task 10: Snapshot Archive + Full Verification

**Files:**
- Modify: `order-adapter/src/main/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManager.java`
- Modify: `order-infrastructure/src/main/resources/db/migration/V10__create_saga_logs_archive.sql`

**Interfaces:**
- Consumes: `DataLifecycleManager` (Task 4), `OrderSnapshotJpaRepository` (Task 8)
- Produces: Snapshot archive logic in `DataLifecycleManager`; `order_snapshots_archive` table

- [ ] **Step 1: Add order_snapshots_archive to V10 migration**

Append to `order-infrastructure/src/main/resources/db/migration/V10__create_saga_logs_archive.sql`:

```sql

-- Archive table for old order snapshots (older than 90 days)
CREATE TABLE order_snapshots_archive (
    id          BIGINT PRIMARY KEY,
    order_id    VARCHAR(36) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    snapshot    TEXT NOT NULL,
    version     BIGINT NOT NULL,
    reason      VARCHAR(100),
    created_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_snapshots_archive_order_id ON order_snapshots_archive(order_id);
```

- [ ] **Step 2: Add snapshot archive to DataLifecycleManager**

Add `OrderSnapshotJpaRepository` as a dependency to `DataLifecycleManager`:

```java
private final OrderSnapshotJpaRepository snapshotRepo;
```

Add to constructor.

Add archive query to `OrderSnapshotJpaRepository`:

```java
@Modifying
@Query(value = "INSERT INTO order_snapshots_archive " +
       "SELECT id, order_id, status, snapshot, version, reason, created_at " +
       "FROM order_snapshots WHERE created_at < :threshold",
       nativeQuery = true)
int archiveSnapshotsOlderThan(@Param("threshold") LocalDateTime threshold);

@Modifying
@Query(value = "DELETE FROM order_snapshots WHERE created_at < :threshold",
       nativeQuery = true)
int deleteArchivedSnapshotsOlderThan(@Param("threshold") LocalDateTime threshold);
```

Add to `archiveAndCleanup()` method in `DataLifecycleManager`:

```java
LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);

int snapshotsArchived = snapshotRepo.archiveSnapshotsOlderThan(ninetyDaysAgo);
if (snapshotsArchived > 0) {
    snapshotRepo.deleteArchivedSnapshotsOlderThan(ninetyDaysAgo);
}
metricsPort.recordLifecycleArchived(archived + snapshotsArchived);
```

Update the log line to include snapshot count.

- [ ] **Step 3: Update DataLifecycleManagerTest for snapshot archive**

Add verification for snapshot archive in the test:

```java
@Mock private OrderSnapshotJpaRepository snapshotRepo;

// In setUp:
manager = new DataLifecycleManager(sagaLogPort, outboxRepo, compensationRepo, snapshotRepo, metricsPort);

// In archiveAndCleanup_archivesAndDeletes test:
when(snapshotRepo.archiveSnapshotsOlderThan(any())).thenReturn(5);
// verify snapshotRepo.archiveSnapshotsOlderThan(any()) called
// verify metricsPort.recordLifecycleArchived(55) // 50 saga + 5 snapshots
```

- [ ] **Step 4: Run full test suite**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -q`
Expected: All tests pass

- [ ] **Step 5: Run ArchUnit architecture test specifically**

Run: `cd /home/bjdeng/project/java_projects/order-demo && mvn test -pl order-infrastructure -Dtest="ArchitectureTest" -q`
Expected: All architecture rules pass

- [ ] **Step 6: Commit**

```bash
git add order-infrastructure/src/main/resources/db/migration/V10__create_saga_logs_archive.sql \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManager.java \
        order-adapter/src/test/java/com/order/demo/adapter/outbound/lifecycle/DataLifecycleManagerTest.java \
        order-adapter/src/main/java/com/order/demo/adapter/outbound/snapshot/OrderSnapshotJpaRepository.java
git commit -m "feat: add snapshot archive to lifecycle manager (snapshots step 3)"
```

---

## Self-Review

### 1. Spec Coverage

| Spec Section | Task |
|---|---|
| §1.1 Problem (data growth) | Task 1–4 |
| §1.2 Archive strategy (saga_logs → archive, outbox delete, compensation delete) | Task 1, 2, 4 |
| §1.3 Data model (saga_logs_archive) | Task 1 |
| §1.4 Implementation (DataLifecycleManager) | Task 4 |
| §1.5 Architecture adaptation (port, repo, metrics, config) | Tasks 1–4 |
| §2.1 Problem (TEXT blob, manual serialization) | Tasks 5–7 |
| §2.2.1 Database layer (TEXT → JSONB) | Task 5 (V11 migration) |
| §2.2.2 JPA layer (@Convert) | Tasks 5–6 |
| §2.2.3 Query capability (findBySku) | Task 7 |
| §2.3 Architecture adaptation | Tasks 5–7 |
| §3.1 Snapshot table | Task 8 |
| §3.2 Snapshot trigger (saga integration) | Task 9 |
| §3.3 Point-in-time query | Task 9 |
| §3.4 Snapshot archive (90 days) | Task 10 |

### 2. Placeholder Scan

No TBD, TODO, or placeholder patterns found. All steps contain actual code.

### 3. Type Consistency

- `SagaLogPort.archiveCompletedOlderThan(LocalDateTime)` → `int` — matches `SagaLogJpaRepository.archiveCompletedOlderThan(@Param LocalDateTime)` → `int`
- `OutboxEventJpaRepository.deleteByStatusAndSentAtBefore(String, LocalDateTime)` → `int` — matches `DataLifecycleManager` usage
- `CompensationLogJpaRepository.deleteByStatusAndAttemptedAtBefore(String, LocalDateTime)` → `int` — matches `DataLifecycleManager` usage
- `MetricsPort.recordLifecycleArchived(int)` + `recordLifecycleDeleted(int)` — matches `OrderMetrics` implementation
- `OrderSnapshotPort.saveSnapshot(Order, String)` + `findSnapshotAt(String, Instant)` → `Optional<OrderSummary>` — matches adapter implementation
- `OrderQueryPort.findOrderAt(String, Instant)` → `Optional<OrderSummary>` — matches `OrderQueryAdapter` delegation to `OrderSnapshotPort`
- `OrderRepositoryPort.findBySku(String)` → `List<Order>` — matches `OrderPersistenceAdapter` implementation
- `OrderEntity.items` type `List<OrderItem>` with `@Convert(converter = OrderItemListConverter.class)` — matches converter's `AttributeConverter<List<OrderItem>, String>`
- `OrderEntity.reservationIds` type `List<String>` with `@Convert(converter = StringListConverter.class)` — matches converter's `AttributeConverter<List<String>, String>`
