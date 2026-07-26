package com.order.demo.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
