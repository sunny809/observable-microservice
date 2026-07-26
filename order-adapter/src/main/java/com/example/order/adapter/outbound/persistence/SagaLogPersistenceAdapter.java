package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.port.out.SagaLogEntry;
import com.order.demo.application.port.out.SagaLogPort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
        repository.save(new SagaLogEntity(orderId, "COMPENSATION",
            "reservationId=" + reservationId + ", reason=" + reason, LocalDateTime.now()));
    }

    @Override
    public void recordSagaStepStarted(String orderId, String stepName) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStep(stepName);
        entity.setStepName(stepName);
        entity.setStepStatus("PENDING");
        entity.setStartedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDetail("Step started");
        repository.save(entity);
    }

    @Override
    public void recordSagaStepCompleted(String orderId, String stepName, String message) {
        SagaLogEntity entity = findOrCreatePendingStep(orderId, stepName);
        entity.setStepStatus("COMPLETED");
        entity.setCompletedAt(LocalDateTime.now());
        entity.setDetail(message);
        repository.save(entity);
    }

    @Override
    public void recordSagaStepFailed(String orderId, String stepName, String error) {
        SagaLogEntity entity = findOrCreatePendingStep(orderId, stepName);
        entity.setStepStatus("FAILED");
        entity.setCompletedAt(LocalDateTime.now());
        entity.setDetail(error);
        repository.save(entity);
    }

    @Override
    public void recordSagaCompensationRequired(String orderId, String stepName, String reason) {
        SagaLogEntity entity = findOrCreatePendingStep(orderId, stepName);
        entity.setStepStatus("COMPENSATION_REQUIRED");
        entity.setCompletedAt(LocalDateTime.now());
        entity.setDetail(reason);
        repository.save(entity);
    }

    @Override
    public void recordSagaCompensationStarted(String orderId, String stepName) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStep(stepName);
        entity.setStepName(stepName);
        entity.setStepStatus("COMPENSATING");
        entity.setStartedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDetail("Compensation started");
        repository.save(entity);
    }

    @Override
    public void recordSagaCompensationCompleted(String orderId, String stepName) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStep(stepName);
        entity.setStepName(stepName);
        entity.setStepStatus("COMPENSATED");
        entity.setCompletedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDetail("Compensation completed");
        repository.save(entity);
    }

    @Override
    public void recordSagaCompensationFailed(String orderId, String stepName, String error) {
        SagaLogEntity entity = new SagaLogEntity();
        entity.setOrderId(orderId);
        entity.setStep(stepName);
        entity.setStepName(stepName);
        entity.setStepStatus("COMPENSATION_FAILED");
        entity.setCompletedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDetail(error);
        repository.save(entity);
    }

    @Override
    public List<SagaLogEntry> findPendingStepsOlderThan(Duration timeout) {
        LocalDateTime threshold = LocalDateTime.now().minus(timeout);
        return repository.findPendingStepsOlderThan(threshold).stream()
            .map(e -> new SagaLogEntry(
                e.getId(), e.getOrderId(), e.getStepName(),
                e.getStepStatus(), e.getStartedAt(), e.getCompletedAt(),
                e.getDetail()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Finds the latest PENDING step for the given order and step name, or creates
     * a new PENDING entity if none exists. This ensures that status transitions
     * (COMPLETED, FAILED, COMPENSATION_REQUIRED) update the existing row rather
     * than inserting duplicate rows.
     */
    private SagaLogEntity findOrCreatePendingStep(String orderId, String stepName) {
        return repository.findLatestPendingStep(orderId, stepName)
            .orElseGet(() -> {
                SagaLogEntity entity = new SagaLogEntity();
                entity.setOrderId(orderId);
                entity.setStep(stepName);
                entity.setStepName(stepName);
                entity.setStepStatus("PENDING");
                entity.setStartedAt(LocalDateTime.now());
                entity.setCreatedAt(LocalDateTime.now());
                return entity;
            });
    }
}