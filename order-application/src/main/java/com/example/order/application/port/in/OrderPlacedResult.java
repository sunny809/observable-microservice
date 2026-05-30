package com.example.order.application.port.in;

import com.example.order.application.domain.OrderStatus;
import java.util.Objects;

public class OrderPlacedResult {
    private final String orderId;
    private final OrderStatus status;
    private final String traceId;

    public OrderPlacedResult(String orderId, OrderStatus status, String traceId) {
        this.orderId = Objects.requireNonNull(orderId, "orderId is required");
        this.status = Objects.requireNonNull(status, "status is required");
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
