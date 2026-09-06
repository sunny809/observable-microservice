package com.order.demo.application.domain;

/**
 * Thrown when an order aggregate cannot be found by its ID.
 *
 * <p>Lives in the domain layer (alongside {@link DuplicateOrderException} and
 * {@link InsufficientInventoryException}) so that application-layer use cases
 * such as the cancellation saga can signal a missing order without depending
 * on any adapter.
 */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
