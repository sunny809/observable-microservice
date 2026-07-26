package com.order.demo.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompensationLogJpaRepository extends JpaRepository<CompensationLogEntity, String> {
}