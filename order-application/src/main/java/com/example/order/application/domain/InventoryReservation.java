package com.example.order.application.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable value object representing an inventory reservation for a single SKU.
 *
 * <p>A reservation tracks the lifecycle of inventory held for an order:
 * {@code PENDING} → {@code CONFIRMED} (when WMS accepts) or {@code RELEASED}
 * (when compensation is triggered).
 *
 * <p>Created by {@link #pending(String, int, String)} and transitioned
 * by {@link #confirm()} and {@link #release()}.
 *
 * @see ReservationStatus
 * @see OrderPlacementSaga
 */
public class InventoryReservation {
    private final String reservationId;
    private final String sku;
    private final int quantity;
    private final String orderId;
    private ReservationStatus status;
    private final Instant createdTime;
    private Instant confirmedTime;

    public InventoryReservation(String reservationId,
                                String sku,
                                int quantity,
                                String orderId,
                                ReservationStatus status,
                                Instant createdTime,
                                Instant confirmedTime) {
        this.reservationId = Objects.requireNonNull(reservationId);
        this.sku = Objects.requireNonNull(sku);
        this.quantity = quantity;
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.orderId = Objects.requireNonNull(orderId);
        this.status = Objects.requireNonNull(status);
        this.createdTime = Objects.requireNonNull(createdTime);
        this.confirmedTime = confirmedTime;
    }

    public static InventoryReservation pending(String sku, int quantity, String orderId) {
        return new InventoryReservation(
                UUID.randomUUID().toString(),
                sku,
                quantity,
                orderId,
                ReservationStatus.PENDING,
                Instant.now(),
                null);
    }

    /**
     * Creates a reservation with a specific reservation ID.
     * Used for reconstructing reservations from persisted data (e.g., WMS callback).
     */
    public static InventoryReservation withId(String reservationId, String sku,
                                              int quantity, String orderId) {
        return new InventoryReservation(reservationId, sku, quantity, orderId,
                ReservationStatus.PENDING, Instant.now(), null);
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedTime = Instant.now();
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public Instant getConfirmedTime() {
        return confirmedTime;
    }
}
