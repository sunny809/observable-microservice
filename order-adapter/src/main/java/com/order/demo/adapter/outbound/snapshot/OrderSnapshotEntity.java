package com.order.demo.adapter.outbound.snapshot;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_snapshots")
public class OrderSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "snapshot", nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public OrderSnapshotEntity() {}

    // Getters
    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getSnapshot() { return snapshot; }
    public Long getVersion() { return version; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setStatus(String status) { this.status = status; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public void setVersion(Long version) { this.version = version; }
    public void setReason(String reason) { this.reason = reason; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
