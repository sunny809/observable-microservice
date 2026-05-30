package com.example.order.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaLogJpaRepository extends JpaRepository<SagaLogEntity, Long> {
}
