package com.order.demo.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SagaLogJpaRepository extends JpaRepository<SagaLogEntity, Long> {

    @Query("SELECT s FROM SagaLogEntity s WHERE s.stepStatus = 'PENDING' AND s.startedAt < :threshold")
    List<SagaLogEntity> findPendingStepsOlderThan(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT s FROM SagaLogEntity s WHERE s.orderId = :orderId AND s.stepName = :stepName AND s.stepStatus = 'PENDING' ORDER BY s.id DESC")
    Optional<SagaLogEntity> findLatestPendingStep(@Param("orderId") String orderId, @Param("stepName") String stepName);

    List<SagaLogEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);

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
}
