package com.order.demo.application.port.in;

/**
 * Inbound command for cancelling an order.
 *
 * @param orderId    the ID of the order to cancel
 * @param reason     the cancellation reason (for audit / compensation log)
 * @param customerId the customer initiating the cancellation (ownership check)
 */
public record CancelOrderCommand(String orderId, String reason, String customerId) {
    public CancelOrderCommand {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
    }
}
