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
                                CompensationLogJpaRepository compensationRepo,
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
