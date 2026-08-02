package com.order.demo.adapter.outbound.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findTop50ByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("DELETE FROM OutboxEventEntity e WHERE e.status = :status AND e.sentAt < :threshold")
    int deleteByStatusAndSentAtBefore(@Param("status") String status,
                                      @Param("threshold") LocalDateTime threshold);
}
