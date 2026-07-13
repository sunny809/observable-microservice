package com.example.order.adapter.outbound.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "compensation_logs")
public class CompensationLogEntity {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "error_message")
    private String errorMessage;

    public CompensationLogEntity() {}

    public CompensationLogEntity(String idempotencyKey, String orderId, String stepName,
                                 String reservationId, String status, String errorMessage) {
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.stepName = stepName;
        this.reservationId = reservationId;
        this.status = status;
        this.attemptedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    // getters
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOrderId() { return orderId; }
    public String getStepName() { return stepName; }
    public String getReservationId() { return reservationId; }
    public String getStatus() { return status; }
    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public String getErrorMessage() { return errorMessage; }
}