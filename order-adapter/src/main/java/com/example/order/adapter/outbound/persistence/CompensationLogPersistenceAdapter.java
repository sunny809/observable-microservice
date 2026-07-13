package com.example.order.adapter.outbound.persistence;

import com.example.order.application.port.out.CompensationLogPort;
import com.example.order.application.port.out.CompensationStatus;
import org.springframework.stereotype.Component;

@Component
public class CompensationLogPersistenceAdapter implements CompensationLogPort {

    private final CompensationLogJpaRepository repository;

    public CompensationLogPersistenceAdapter(CompensationLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String idempotencyKey) {
        return repository.existsById(idempotencyKey);
    }

    @Override
    public void save(String idempotencyKey, String orderId, String stepName,
                     String reservationId, CompensationStatus status, String errorMessage) {
        CompensationLogEntity entity = new CompensationLogEntity(
            idempotencyKey, orderId, stepName, reservationId,
            status.name(), errorMessage
        );
        repository.save(entity);
    }
}
