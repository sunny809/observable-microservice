package com.order.demo.adapter.outbound.persistence;

import com.order.demo.adapter.outbound.persistence.OrderItemListConverter;
import com.order.demo.adapter.outbound.persistence.StringListConverter;
import com.order.demo.application.port.in.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Convert(converter = OrderItemListConverter.class)
    @Column(name = "items", columnDefinition = "TEXT")
    private List<OrderItem> items;

    @Convert(converter = StringListConverter.class)
    @Column(name = "reservation_ids", columnDefinition = "TEXT")
    private List<String> reservationIds;

    @Version
    @Column(name = "version")
    private Long version;

    public OrderEntity() {
    }

    public OrderEntity(String id, String customerId, String idempotencyKey, String reservationId, String status, LocalDateTime createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.reservationId = reservationId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public List<String> getReservationIds() {
        return reservationIds;
    }

    public void setReservationIds(List<String> reservationIds) {
        this.reservationIds = reservationIds;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
