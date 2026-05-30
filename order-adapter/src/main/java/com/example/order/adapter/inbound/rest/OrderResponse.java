package com.example.order.adapter.inbound.rest;

import com.example.order.application.domain.OrderStatus;

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
