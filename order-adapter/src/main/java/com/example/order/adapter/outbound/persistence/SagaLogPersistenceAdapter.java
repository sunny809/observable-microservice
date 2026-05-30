package com.example.order.adapter.outbound.persistence;

import com.example.order.application.port.out.SagaLogPort;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SagaLogPersistenceAdapter implements SagaLogPort {

    private final SagaLogJpaRepository repository;

    public SagaLogPersistenceAdapter(SagaLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordStep(String orderId, String step, String detail) {
        repository.save(new SagaLogEntity(orderId, step, detail, LocalDateTime.now()));
    }

    @Override
    public void recordCompensation(String orderId, String reservationId, String reason) {
        repository.save(new SagaLogEntity(orderId, "COMPENSATION", "reservationId=" + reservationId + ", reason=" + reason, LocalDateTime.now()));
    }
}
