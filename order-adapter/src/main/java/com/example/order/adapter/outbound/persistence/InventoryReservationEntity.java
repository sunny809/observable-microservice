package com.example.order.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservationEntity {

    @Id
    @Column(name = "reservation_id", nullable = false, length = 36)
    private String reservationId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Column(name = "confirmed_time")
    private LocalDateTime confirmedTime;

    public InventoryReservationEntity() {
    }

    public InventoryReservationEntity(String reservationId, String sku, int quantity, String orderId, String status, LocalDateTime createdTime, LocalDateTime confirmedTime) {
        this.reservationId = reservationId;
        this.sku = sku;
        this.quantity = quantity;
        this.orderId = orderId;
        this.status = status;
        this.createdTime = createdTime;
        this.confirmedTime = confirmedTime;
    }
}
