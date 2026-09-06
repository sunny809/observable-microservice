package com.order.demo.application.domain;

import java.util.Objects;

/**
 * Domain event published after an order has been successfully cancelled.
 *
 * <p>Published by {@link com.order.demo.application.service.OrderCancellationSaga}
 * once compensation (inventory release, WMS void) has completed and the order
 * status is {@link OrderStatus#CANCELLED}.
 */
public class OrderCancelledEvent {
    private final String orderId;
    private final String reason;

    public OrderCancelledEvent(String orderId, String reason) {
        this.orderId = Objects.requireNonNull(orderId, "orderId is required");
        this.reason = reason;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }
}
