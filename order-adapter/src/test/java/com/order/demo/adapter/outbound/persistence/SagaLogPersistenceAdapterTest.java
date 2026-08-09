package com.order.demo.adapter.outbound.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
}
