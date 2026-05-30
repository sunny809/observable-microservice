package com.example.order.application.port.out;

public interface SagaLogPort {
    void recordStep(String orderId, String step, String detail);

    void recordCompensation(String orderId, String reservationId, String reason);
}
