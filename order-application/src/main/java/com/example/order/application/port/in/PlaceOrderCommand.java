package com.example.order.application.port.in;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Command object that represents a request to place an order.
 * <p>Immutable and intended as an input port DTO for application use-cases.
 */
public class PlaceOrderCommand {
    private final String customerId;
    private final List<OrderItem> items;
    private final String idempotencyKey;

    public PlaceOrderCommand(String customerId, List<OrderItem> items, String idempotencyKey) {
        this.customerId = Objects.requireNonNull(customerId, "customerId is required");
        this.items = Collections.unmodifiableList(Objects.requireNonNull(items, "items are required"));
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
    }

    /**
     * Static factory for improved readability at call sites.
     * Example: {@code PlaceOrderCommand.of(customerId, items, idempotencyKey)}
     */
    public static PlaceOrderCommand of(String customerId, List<OrderItem> items, String idempotencyKey) {
        return new PlaceOrderCommand(customerId, items, idempotencyKey);
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
