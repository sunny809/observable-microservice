package com.order.demo.adapter.outbound.lifecycle;

import com.order.demo.adapter.outbound.outbox.OutboxEventJpaRepository;
import com.order.demo.adapter.outbound.persistence.CompensationLogJpaRepository;
import com.order.demo.adapter.outbound.snapshot.OrderSnapshotJpaRepository;
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
    @Mock private OrderSnapshotJpaRepository snapshotRepo;
    @Mock private MetricsPort metricsPort;

    private DataLifecycleManager manager;

    @BeforeEach
    void setUp() {
        manager = new DataLifecycleManager(sagaLogPort, outboxRepo, compensationRepo, snapshotRepo, metricsPort);
    }

    @Test
    @DisplayName("archiveAndCleanup archives saga logs and deletes outbox/compensation records")
    void archiveAndCleanup_archivesAndDeletes() {
        when(sagaLogPort.archiveCompletedOlderThan(any())).thenReturn(50);
        when(outboxRepo.deleteByStatusAndSentAtBefore(eq("SENT"), any())).thenReturn(30);
        when(compensationRepo.deleteByStatusAndAttemptedAtBefore(eq("COMPLETED"), any())).thenReturn(10);
        when(snapshotRepo.archiveSnapshotsOlderThan(any())).thenReturn(5);

        manager.archiveAndCleanup();

        verify(sagaLogPort).archiveCompletedOlderThan(any());
        verify(outboxRepo).deleteByStatusAndSentAtBefore(eq("SENT"), any());
        verify(compensationRepo).deleteByStatusAndAttemptedAtBefore(eq("COMPLETED"), any());
        verify(snapshotRepo).archiveSnapshotsOlderThan(any());
        verify(snapshotRepo).deleteArchivedSnapshotsOlderThan(any());
        verify(metricsPort).recordLifecycleArchived(55);
        verify(metricsPort).recordLifecycleDeleted(40);
    }

    @Test
    @DisplayName("archiveAndCleanup with zero rows still records metrics")
    void archiveAndCleanup_zeroRows_recordsZeroMetrics() {
        when(sagaLogPort.archiveCompletedOlderThan(any())).thenReturn(0);
        when(outboxRepo.deleteByStatusAndSentAtBefore(eq("SENT"), any())).thenReturn(0);
        when(compensationRepo.deleteByStatusAndAttemptedAtBefore(eq("COMPLETED"), any())).thenReturn(0);
        when(snapshotRepo.archiveSnapshotsOlderThan(any())).thenReturn(0);

        manager.archiveAndCleanup();

        verify(snapshotRepo).archiveSnapshotsOlderThan(any());
        verify(snapshotRepo, never()).deleteArchivedSnapshotsOlderThan(any());
        verify(metricsPort).recordLifecycleArchived(0);
        verify(metricsPort).recordLifecycleDeleted(0);
    }
}
