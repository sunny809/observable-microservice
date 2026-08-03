package com.order.demo.adapter.inbound.rest;

import com.order.demo.application.domain.OrderStatus;

/**
 * DTO for the place order REST response.
 *
 * <p>Returned with HTTP 201 Created and a {@code Location} header
 * pointing to the newly created order.
 *
 * @see OrderController#placeOrder
 */
public class OrderResponse {
    private String orderId;
    private OrderStatus status;
    private String traceId;

    public OrderResponse(String orderId, OrderStatus status, String traceId) {
        this.orderId = orderId;
        this.status = status;
        this.traceId = traceId;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getTraceId() {
        return traceId;
    }
}
