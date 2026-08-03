package com.order.demo.adapter.outbound.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("UPDATE OrderEntity o SET o.status = :status WHERE o.id = :id")
    void updateStatus(@Param("id") String id, @Param("status") String status);

    @Modifying
    @Query("UPDATE OrderEntity o SET o.status = :newStatus, o.version = o.version + 1 " +
           "WHERE o.id = :id AND o.status = :expectedStatus AND o.version = :expectedVersion")
    int updateStatusWithVersion(@Param("id") String id,
                                @Param("newStatus") String newStatus,
                                @Param("expectedStatus") String expectedStatus,
                                @Param("expectedVersion") Long expectedVersion);

    /**
     * Finds orders containing an item with the given SKU using LIKE for H2 compatibility.
     * The skuPattern should be formatted as %"sku":"SKU-VALUE"% by the adapter.
     * In production with PostgreSQL, this can be upgraded to items @> :skuFilter::jsonb
     * for index-backed queries.
     */
    @Query(value = "SELECT * FROM orders WHERE items LIKE :skuPattern", nativeQuery = true)
    List<OrderEntity> findByItemsContainingSku(@Param("skuPattern") String skuPattern);
}
