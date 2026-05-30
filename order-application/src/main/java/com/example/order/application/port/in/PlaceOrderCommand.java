package com.example.order.application.port.in;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PlaceOrderCommand {
    private final String customerId;
    private final List<OrderItem> items;
    private final String idempotencyKey;

    public PlaceOrderCommand(String customerId, List<OrderItem> items, String idempotencyKey) {
        this.customerId = Objects.requireNonNull(customerId, "customerId is required");
        this.items = Collections.unmodifiableList(Objects.requireNonNull(items, "items are required"));
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
