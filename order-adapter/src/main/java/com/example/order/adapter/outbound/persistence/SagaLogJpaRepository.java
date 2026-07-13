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
