package com.order.demo.adapter.inbound.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * REST request body for cancelling an order.
 */
public class CancelOrderRequest {

    @NotBlank(message = "reason is required")
    private String reason;

    @NotBlank(message = "customerId is required")
    private String customerId;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
