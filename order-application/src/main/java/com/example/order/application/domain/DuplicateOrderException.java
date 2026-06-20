package com.example.order.application.domain;

/**
 * Thrown when a client attempts to place an order using an idempotency key
 * that has already been processed.
 *
 * <p>Maps to HTTP 409 Conflict in the REST layer.
 *
 * @see com.example.order.application.service.OrderPlacementSaga#placeOrder
 */
public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String idempotencyKey) {
        super("Duplicate order request detected for key: " + idempotencyKey);
    }
}
