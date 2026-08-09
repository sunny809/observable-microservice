package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.port.out.SagaLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class SagaLogPersistenceAdapterTest {

    private final SagaLogJpaRepository repository = mock(SagaLogJpaRepository.class);
    private final SagaLogPersistenceAdapter adapter = new SagaLogPersistenceAdapter(repository);

    @Test
    @DisplayName("recordStep should save entity with correct orderId, step, and detail")
    void testRecordStepCallsRepositoryWithCorrectValues() {
        adapter.recordStep("ord-1", "ORDER_CREATED", "Order placed successfully");

        verify(repository).save(argThat(entity ->
                "ord-1".equals(entity.getOrderId())
                && "ORDER_CREATED".equals(entity.getStep())
                && "Order placed successfully".equals(entity.getDetail())
                && entity.getCreatedAt() != null
        ));
    }

    @Test
    @DisplayName("recordCompensation should save entity with COMPENSATION step and reservationId detail")
    void testRecordCompensationCallsRepositoryWithCorrectValues() {
        adapter.recordCompensation("ord-1", "resv-123", "WMS rejected shipment");

        verify(repository).save(argThat(entity ->
                "ord-1".equals(entity.getOrderId())
                && "COMPENSATION".equals(entity.getStep())
                && entity.getDetail() != null && entity.getDetail().contains("resv-123")
                && entity.getDetail().contains("WMS rejected shipment")
                && entity.getCreatedAt() != null
        ));
    }

    @Test
    @DisplayName("recordStep should capture current timestamp between before and after")
    void testRecordStepUsesCurrentTime() {
        LocalDateTime before = LocalDateTime.now();
        adapter.recordStep("ord-1", "STEP", "detail");
        LocalDateTime after = LocalDateTime.now();

        verify(repository).save(argThat(entity -> {
            LocalDateTime createdAt = entity.getCreatedAt();
            return createdAt != null
                    && !createdAt.isBefore(before)
                    && !createdAt.isAfter(after);
        }));
    }

    @Test
    @DisplayName("recordCompensation should capture current timestamp between before and after")
    void testRecordCompensationUsesCurrentTime() {
        LocalDateTime before = LocalDateTime.now();
        adapter.recordCompensation("ord-1", "RELEASE", "compensation detail");
        LocalDateTime after = LocalDateTime.now();

        verify(repository).save(argThat(entity -> {
            LocalDateTime createdAt = entity.getCreatedAt();
            return createdAt != null
                    && !createdAt.isBefore(before)
                    && !createdAt.isAfter(after);
        }));
    }

    @Test
    @DisplayName("recordSagaStepCompleted with audit fields should set previousStatus, newStatus, and changedBy")
    void testRecordSagaStepCompletedWithAuditFields() {
        when(repository.findLatestPendingStep("order-1", "WMS_ACKED"))
                .thenReturn(java.util.Optional.empty());

        adapter.recordSagaStepCompleted("order-1", "WMS_ACKED",
                "WMS accepted", "CREATED", "WMS_ACKED");

        verify(repository).save(argThat(entity ->
                "CREATED".equals(entity.getPreviousStatus())
                && "WMS_ACKED".equals(entity.getNewStatus())
                && "SYSTEM".equals(entity.getChangedBy())
                && "COMPLETED".equals(entity.getStepStatus())
        ));
    }

    @Test
    @DisplayName("archiveCompletedOlderThan archives and deletes when rows found")
    void archiveCompletedOlderThan_withRows_foundArchivesAndDeletes() {
        when(repository.archiveCompletedOlderThan(any())).thenReturn(10);

        int result = adapter.archiveCompletedOlderThan(LocalDateTime.now().minusDays(30));

        assertEquals(10, result);
        verify(repository).archiveCompletedOlderThan(any());
        verify(repository).deleteArchivedOlderThan(any());
    }

    @Test
    @DisplayName("archiveCompletedOlderThan does not delete when no rows found")
    void archiveCompletedOlderThan_withNoRows_skipsDelete() {
        when(repository.archiveCompletedOlderThan(any())).thenReturn(0);

        int result = adapter.archiveCompletedOlderThan(LocalDateTime.now().minusDays(30));

        assertEquals(0, result);
        verify(repository).archiveCompletedOlderThan(any());
        verify(repository, never()).deleteArchivedOlderThan(any());
    }

    @Test
    @DisplayName("recordSagaStepFailed with audit fields should set previousStatus, newStatus, and changedBy")
    void testRecordSagaStepFailedWithAuditFields() {
        when(repository.findLatestPendingStep("order-1", "WMS_ACKED"))
                .thenReturn(java.util.Optional.empty());

        adapter.recordSagaStepFailed("order-1", "WMS_ACKED",
                "WMS rejected", "CREATED", "FAILED");

        verify(repository).save(argThat(entity ->
                "CREATED".equals(entity.getPreviousStatus())
                && "FAILED".equals(entity.getNewStatus())
                && "SYSTEM".equals(entity.getChangedBy())
                && "FAILED".equals(entity.getStepStatus())
        ));
    }

    @Test
    @DisplayName("recordSagaStepStarted should save a PENDING entity")
    void recordSagaStepStarted_savesPendingEntity() {
        adapter.recordSagaStepStarted("order-1", "RESERVE_INVENTORY");

        verify(repository).save(argThat(entity ->
                "order-1".equals(entity.getOrderId())
                && "RESERVE_INVENTORY".equals(entity.getStep())
                && "RESERVE_INVENTORY".equals(entity.getStepName())
                && "PENDING".equals(entity.getStepStatus())
                && entity.getStartedAt() != null
                && "Step started".equals(entity.getDetail())
        ));
    }

    @Test
    @DisplayName("recordSagaStepCompleted (simple) should update to COMPLETED")
    void recordSagaStepCompleted_simple_updatesToCompleted() {
        when(repository.findLatestPendingStep("order-1", "RESERVE_INVENTORY"))
                .thenReturn(java.util.Optional.empty());

        adapter.recordSagaStepCompleted("order-1", "RESERVE_INVENTORY", "Reserved successfully");

        verify(repository).save(argThat(entity ->
                "COMPLETED".equals(entity.getStepStatus())
                && entity.getCompletedAt() != null
                && "Reserved successfully".equals(entity.getDetail())
        ));
    }

    @Test
    @DisplayName("recordSagaStepFailed (simple) should update to FAILED")
    void recordSagaStepFailed_simple_updatesToFailed() {
        when(repository.findLatestPendingStep("order-1", "RESERVE_INVENTORY"))
                .thenReturn(java.util.Optional.empty());

        adapter.recordSagaStepFailed("order-1", "RESERVE_INVENTORY", "Reservation failed");

        verify(repository).save(argThat(entity ->
                "FAILED".equals(entity.getStepStatus())
                && entity.getCompletedAt() != null
                && "Reservation failed".equals(entity.getDetail())
        ));
    }

    @Test
    @DisplayName("recordSagaCompensationRequired should save COMPENSATION_REQUIRED entity")
    void recordSagaCompensationRequired_savesCompensationRequired() {
        when(repository.findLatestPendingStep("order-1", "RESERVE_INVENTORY"))
                .thenReturn(java.util.Optional.empty());

        adapter.recordSagaCompensationRequired("order-1", "RESERVE_INVENTORY", "Need to release inventory");

        verify(repository).save(argThat(entity ->
                "COMPENSATION_REQUIRED".equals(entity.getStepStatus())
                && entity.getCompletedAt() != null
                && "Need to release inventory".equals(entity.getDetail())
        ));
    }

    @Test
    @DisplayName("recordSagaCompensationStarted should save COMPENSATING entity")
    void recordSagaCompensationStarted_savesCompensating() {
        adapter.recordSagaCompensationStarted("order-1", "RESERVE_INVENTORY");

        verify(repository).save(argThat(entity ->
                "order-1".equals(entity.getOrderId())
                && "RESERVE_INVENTORY".equals(entity.getStep())
                && "COMPENSATING".equals(entity.getStepStatus())
                && entity.getStartedAt() != null
                && "Compensation started".equals(entity.getDetail())
        ));
    }

    @Test
    @DisplayName("recordSagaCompensationCompleted should save COMPENSATED entity")
    void recordSagaCompensationCompleted_savesCompensated() {
        adapter.recordSagaCompensationCompleted("order-1", "RESERVE_INVENTORY");

        verify(repository).save(argThat(entity ->
                "order-1".equals(entity.getOrderId())
                && "RESERVE_INVENTORY".equals(entity.getStep())
                && "COMPENSATED".equals(entity.getStepStatus())
                && entity.getCompletedAt() != null
                && "Compensation completed".equals(entity.getDetail())
        ));
    }

    @Test
    @DisplayName("recordSagaCompensationFailed should save COMPENSATION_FAILED entity")
    void recordSagaCompensationFailed_savesCompensationFailed() {
        adapter.recordSagaCompensationFailed("order-1", "RESERVE_INVENTORY", "Release failed");

        verify(repository).save(argThat(entity ->
                "order-1".equals(entity.getOrderId())
                && "RESERVE_INVENTORY".equals(entity.getStep())
                && "COMPENSATION_FAILED".equals(entity.getStepStatus())
                && entity.getCompletedAt() != null
                && "Release failed".equals(entity.getDetail())
        ));
    }

    @Test
    @DisplayName("findByOrderId should map entities to SagaLogEntry list")
    void findByOrderId_mapsEntities() {
        SagaLogEntity entity = new SagaLogEntity("order-1", "ORDER_CREATED", "Order created",
                LocalDateTime.of(2026, 8, 1, 10, 0));
        entity.setId(1L);
        entity.setStepName("ORDER_CREATED");
        entity.setStepStatus("COMPLETED");
        entity.setStartedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        entity.setCompletedAt(LocalDateTime.of(2026, 8, 1, 10, 1));
        entity.setPreviousStatus("CREATED");
        entity.setNewStatus("RESERVED");
        entity.setChangedBy("SYSTEM");
        when(repository.findByOrderIdOrderByCreatedAtAsc("order-1")).thenReturn(List.of(entity));

        List<SagaLogEntry> result = adapter.findByOrderId("order-1");

        assertThat(result).hasSize(1);
        SagaLogEntry entry = result.get(0);
        assertEquals("order-1", entry.orderId());
        assertEquals("ORDER_CREATED", entry.stepName());
        assertEquals("COMPLETED", entry.stepStatus());
        assertEquals("SYSTEM", entry.changedBy());
        verify(repository).findByOrderIdOrderByCreatedAtAsc("order-1");
    }

    @Test
    @DisplayName("findPendingStepsOlderThan should return mapped entries")
    void findPendingStepsOlderThan_returnsMappedEntries() {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setId(1L);
        entity.setOrderId("order-1");
        entity.setStepName("RESERVE_INVENTORY");
        entity.setStepStatus("PENDING");
        entity.setStartedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        when(repository.findPendingStepsOlderThan(any(LocalDateTime.class))).thenReturn(List.of(entity));

        List<SagaLogEntry> result = adapter.findPendingStepsOlderThan(Duration.ofMinutes(30));

        assertThat(result).hasSize(1);
        assertEquals("order-1", result.get(0).orderId());
        assertEquals("RESERVE_INVENTORY", result.get(0).stepName());
        verify(repository).findPendingStepsOlderThan(any(LocalDateTime.class));
    }
}
