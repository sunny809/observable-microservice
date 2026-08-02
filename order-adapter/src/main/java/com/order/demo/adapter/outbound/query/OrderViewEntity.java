package com.order.demo.adapter.outbound.query;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_view")
@Immutable
public class OrderViewEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "version")
    private Long version;

    @Column(name = "reservation_count")
    private Integer reservationCount;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "last_saga_step")
    private String lastSagaStep;

    @Column(name = "last_saga_status")
    private String lastSagaStatus;

    @Column(name = "last_saga_step_at")
    private LocalDateTime lastSagaStepAt;

    // Getters
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getVersion() { return version; }
    public Integer getReservationCount() { return reservationCount; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public String getLastSagaStep() { return lastSagaStep; }
    public String getLastSagaStatus() { return lastSagaStatus; }
    public LocalDateTime getLastSagaStepAt() { return lastSagaStepAt; }

    // Setters (for test support)
    public void setId(String id) { this.id = id; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setVersion(Long version) { this.version = version; }
    public void setReservationCount(Integer reservationCount) { this.reservationCount = reservationCount; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    public void setLastSagaStep(String lastSagaStep) { this.lastSagaStep = lastSagaStep; }
    public void setLastSagaStatus(String lastSagaStatus) { this.lastSagaStatus = lastSagaStatus; }
    public void setLastSagaStepAt(LocalDateTime lastSagaStepAt) { this.lastSagaStepAt = lastSagaStepAt; }
}
