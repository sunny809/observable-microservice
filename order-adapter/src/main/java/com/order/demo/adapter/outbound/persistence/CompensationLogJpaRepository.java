package com.order.demo.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface CompensationLogJpaRepository extends JpaRepository<CompensationLogEntity, String> {

    @Modifying
    @Query("DELETE FROM CompensationLogEntity c WHERE c.status = :status AND c.attemptedAt < :threshold")
    int deleteByStatusAndAttemptedAtBefore(@Param("status") String status,
                                           @Param("threshold") LocalDateTime threshold);
}