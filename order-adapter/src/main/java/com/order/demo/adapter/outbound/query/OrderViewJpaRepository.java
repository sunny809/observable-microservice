package com.order.demo.adapter.outbound.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface OrderViewJpaRepository extends JpaRepository<OrderViewEntity, String>,
        JpaSpecificationExecutor<OrderViewEntity> {
    Optional<OrderViewEntity> findById(String id);
}
