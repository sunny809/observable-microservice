package com.order.demo.application.port.in;

import com.order.demo.application.domain.OrderStatus;

/**
 * Result returned after a successful (or idempotent) order cancellation.
 *
 * @param orderId the order ID
 * @param status  the resulting status ({@link OrderStatus#CANCELLED})
 * @param message a short human-readable outcome (e.g. "cancelled", "already cancelled")
 */
public record OrderCancelledResult(String orderId, OrderStatus status, String message) {
}
