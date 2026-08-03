package com.order.demo.adapter.outbound.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO order_snapshots_archive " +
           "SELECT id, order_id, status, snapshot, version, reason, created_at " +
           "FROM order_snapshots WHERE created_at < :threshold",
           nativeQuery = true)
    int archiveSnapshotsOlderThan(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM order_snapshots WHERE created_at < :threshold",
           nativeQuery = true)
    int deleteArchivedSnapshotsOlderThan(@Param("threshold") LocalDateTime threshold);
}
