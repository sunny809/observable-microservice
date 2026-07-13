package com.example.order.adapter.outbound.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_logs")
public class SagaLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "step", nullable = false)
    private String step;

    @Column(name = "detail", nullable = false)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "saga_type")
    private String sagaType;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "step_status")
    private String stepStatus;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "compensation_status")
    private String compensationStatus;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    public SagaLogEntity() {}

    public SagaLogEntity(String orderId, String step, String detail, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.step = step;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    // Getters
    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getStep() { return step; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getSagaType() { return sagaType; }
    public String getStepName() { return stepName; }
    public String getStepStatus() { return stepStatus; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getCompensationStatus() { return compensationStatus; }
    public Integer getRetryCount() { return retryCount; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setStep(String step) { this.step = step; }
    public void setDetail(String detail) { this.detail = detail; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setSagaType(String sagaType) { this.sagaType = sagaType; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public void setStepStatus(String stepStatus) { this.stepStatus = stepStatus; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public void setCompensationStatus(String compensationStatus) { this.compensationStatus = compensationStatus; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
}