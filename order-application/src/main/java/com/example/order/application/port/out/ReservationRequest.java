package com.example.order.application.port.out;

import java.util.Objects;

public class ReservationRequest {
    private final String sku;
    private final int quantity;
    private final String orderId;

    public ReservationRequest(String sku, int quantity, String orderId) {
        this.sku = Objects.requireNonNull(sku, "sku is required");
        this.quantity = quantity;
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        this.orderId = Objects.requireNonNull(orderId, "orderId is required");
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
}
