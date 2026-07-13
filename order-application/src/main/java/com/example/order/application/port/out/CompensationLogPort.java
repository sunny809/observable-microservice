package com.example.order.application.port.out;

public interface CompensationLogPort {
    boolean exists(String idempotencyKey);

    void save(String idempotencyKey, String orderId, String stepName,
              String reservationId, CompensationStatus status, String errorMessage);
}