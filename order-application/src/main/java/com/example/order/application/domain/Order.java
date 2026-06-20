package com.example.order.application.domain;

import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.in.PlaceOrderCommand;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing a customer order in the order placement saga.
 *
 * <p>An {@code Order} tracks its lifecycle through {@link OrderStatus} transitions:
 * {@code CREATED} → {@code RESERVED} → {@code WMS_ACKED} or {@code REJECTED}.
 * The order is created after successful inventory reservation and updated
 * asynchronously when the WMS responds.
 *
 * <p>Each order is uniquely identified by an {@code orderId} and deduplicated
 * by its {@code idempotencyKey}.
 *
 * @see OrderStatus
 * @see OrderPlacementSaga
 */
public class Order {
    private final String orderId;
    private final String customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final String idempotencyKey;
    private String reservationId;
    private final Instant createdAt;

    public Order(String orderId,
                 String customerId,
                 List<OrderItem> items,
                 OrderStatus status,
                 String idempotencyKey,
                 String reservationId,
                 Instant createdAt) {
        this.orderId = Objects.requireNonNull(orderId);
        this.customerId = Objects.requireNonNull(customerId);
        this.items = Collections.unmodifiableList(Objects.requireNonNull(items));
        this.status = Objects.requireNonNull(status);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.reservationId = reservationId;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Order create(PlaceOrderCommand command, String reservationId) {
        return new Order(
                UUID.randomUUID().toString(),
                command.getCustomerId(),
                command.getItems(),
                OrderStatus.CREATED,
                command.getIdempotencyKey(),
                reservationId,
                Instant.now());
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Updates the order status. This setter is intentionally exposed for
     * saga state transitions; consider using domain-specific transition
     * methods (e.g., {@code markAsWmsAcked()}) in future refactors.
     *
     * @param status the new status
     */
    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReservationId() {
        return reservationId;
    }

    /**
     * Updates the reservation ID. Exposed for saga orchestration.
     *
     * @param reservationId the new reservation ID
     */
    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
