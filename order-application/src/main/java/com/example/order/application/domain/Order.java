package com.example.order.application.domain;

import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.in.PlaceOrderCommand;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final List<String> allReservationIds;

    public Order(String orderId,
                 String customerId,
                 List<OrderItem> items,
                 OrderStatus status,
                 String idempotencyKey,
                 String reservationId,
                 Instant createdAt,
                 List<String> allReservationIds) {
        this.orderId = Objects.requireNonNull(orderId);
        this.customerId = Objects.requireNonNull(customerId);
        this.items = Collections.unmodifiableList(Objects.requireNonNull(items));
        this.status = Objects.requireNonNull(status);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.reservationId = reservationId;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.allReservationIds = allReservationIds != null
                ? List.copyOf(allReservationIds) : List.of();
    }

    public Order(String orderId,
                 String customerId,
                 List<OrderItem> items,
                 OrderStatus status,
                 String idempotencyKey,
                 String reservationId,
                 Instant createdAt) {
        this(orderId, customerId, items, status, idempotencyKey, reservationId,
             createdAt, List.of());
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
     * Updates the order status. Validates that the transition is legal
     * according to the saga state machine.
     *
     * @param status the new status
     * @throws IllegalStateException if the transition is not allowed from the current status
     */
    public void setStatus(OrderStatus status) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(this.status);
        if (allowed == null || !allowed.contains(status)) {
            throw new IllegalStateException(
                    "Illegal status transition: " + this.status + " → " + status);
        }
        this.status = status;
    }

    /**
     * Transitions the order to {@link OrderStatus#WMS_ACKED}.
     *
     * @throws IllegalStateException if the current status is not {@code CREATED}
     */
    public void markWmsAcked() {
        setStatus(OrderStatus.WMS_ACKED);
    }

    /**
     * Transitions the order to {@link OrderStatus#WMS_PICKED}.
     *
     * @throws IllegalStateException if the current status is not {@code WMS_ACKED}
     */
    public void markWmsPicked() {
        setStatus(OrderStatus.WMS_PICKED);
    }

    /**
     * Transitions the order to {@link OrderStatus#TMS_DISPATCHED}.
     *
     * @throws IllegalStateException if the current status is not {@code WMS_PICKED}
     */
    public void markTmsDispatched() {
        setStatus(OrderStatus.TMS_DISPATCHED);
    }

    /**
     * Transitions the order to {@link OrderStatus#REJECTED}.
     *
     * @throws IllegalStateException if the current status does not allow rejection
     */
    public void markRejected() {
        setStatus(OrderStatus.REJECTED);
    }

    /**
     * Transitions the order to {@link OrderStatus#TMS_REJECTED}.
     *
     * @throws IllegalStateException if the current status is not {@code WMS_PICKED}
     */
    public void markTmsRejected() {
        setStatus(OrderStatus.TMS_REJECTED);
    }

    /** Legal state transitions for the order saga state machine. */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.CREATED, EnumSet.of(OrderStatus.WMS_ACKED, OrderStatus.REJECTED),
            OrderStatus.WMS_ACKED, EnumSet.of(OrderStatus.WMS_PICKED, OrderStatus.REJECTED),
            OrderStatus.WMS_PICKED, EnumSet.of(OrderStatus.TMS_DISPATCHED, OrderStatus.TMS_REJECTED)
    );

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

    public List<String> getAllReservationIds() {
        return allReservationIds;
    }
}
