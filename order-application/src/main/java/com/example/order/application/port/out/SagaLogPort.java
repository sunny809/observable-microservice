package com.order.demo.application.port.out;

import java.util.List;

public interface SagaLogPort {
    void recordStep(String orderId, String step, String detail);

    void recordCompensation(String orderId, String reservationId, String reason);

    void recordSagaStepStarted(String orderId, String stepName);

    void recordSagaStepCompleted(String orderId, String stepName, String message);

    void recordSagaStepFailed(String orderId, String stepName, String error);

    void recordSagaCompensationRequired(String orderId, String stepName, String reason);

    void recordSagaCompensationStarted(String orderId, String stepName);

    void recordSagaCompensationCompleted(String orderId, String stepName);

    void recordSagaCompensationFailed(String orderId, String stepName, String error);

    List<SagaLogEntry> findPendingStepsOlderThan(java.time.Duration timeout);
}