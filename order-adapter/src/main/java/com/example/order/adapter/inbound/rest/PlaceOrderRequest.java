package com.example.order.adapter.inbound.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Objects;

/**
 * DTO for the place order REST request.
 *
 * <p>Validated by Jakarta Bean Validation and mapped to
 * {@link com.example.order.application.port.in.PlaceOrderCommand}
 * by {@link PlaceOrderMapper}.
 *
 * @see OrderController#placeOrder
 * @see PlaceOrderMapper
 */
public class PlaceOrderRequest {

    @NotBlank
    private String customerId;

    @NotBlank
    private String idempotencyKey;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    /**
     * Nested DTO for a single order item within a place order request.
     */
    public static class OrderItemRequest {
        @NotBlank
        private String sku;

        private int quantity;

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
