package com.order.demo.application.port.out;

import java.util.List;

public interface SagaLogPort {
    void recordStep(String orderId, String step, String detail);

    void recordCompensation(String orderId, String reservationId, String reason);

    void recordSagaStepStarted(String orderId, String stepName);

    void recordSagaStepCompleted(String orderId, String stepName, String message);

    void recordSagaStepFailed(String orderId, String stepName, String error);

    void recordSagaStepCompleted(String orderId, String stepName, String message,
                                  String previousStatus, String newStatus);

    void recordSagaStepFailed(String orderId, String stepName, String error,
                               String previousStatus, String newStatus);

    void recordSagaCompensationRequired(String orderId, String stepName, String reason);

    void recordSagaCompensationStarted(String orderId, String stepName);

    void recordSagaCompensationCompleted(String orderId, String stepName);

    void recordSagaCompensationFailed(String orderId, String stepName, String error);

    List<SagaLogEntry> findPendingStepsOlderThan(java.time.Duration timeout);

    /**
     * Finds all saga log entries for a given order, ordered by creation time.
     *
     * @param orderId the order ID
     * @return list of saga log entries for the order
     */
    List<SagaLogEntry> findByOrderId(String orderId);

    /**
     * Archives completed saga logs older than the given threshold.
     * Moves matching rows from saga_logs to saga_logs_archive, then deletes them.
     *
     * @param threshold only rows with step_status IN ('COMPLETED','FAILED','COMPENSATED')
     *                  AND completed_at before this threshold are archived
     * @return the number of rows archived
     */
    int archiveCompletedOlderThan(java.time.LocalDateTime threshold);
}