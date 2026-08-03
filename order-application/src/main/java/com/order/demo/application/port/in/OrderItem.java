package com.order.demo.application.port.in;

import java.util.Objects;

public class OrderItem {
    private final String sku;
    private final int quantity;

    public OrderItem(String sku, int quantity) {
        this.sku = Objects.requireNonNull(sku, "sku is required");
        this.quantity = quantity;
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }
}
