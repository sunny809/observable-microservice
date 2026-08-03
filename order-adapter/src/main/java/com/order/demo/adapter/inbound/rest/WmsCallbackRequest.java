package com.order.demo.adapter.inbound.rest;

import jakarta.validation.constraints.NotBlank;

public class WmsCallbackRequest {
    @NotBlank
    private String orderId;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
