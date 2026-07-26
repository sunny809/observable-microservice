package com.order.demo.application.domain;

/**
 * Thrown when the inventory service cannot reserve stock for a requested SKU.
 *
 * <p>Triggers compensation in the saga: all previously reserved items
 * are released before this exception is propagated to the caller.
 * Maps to HTTP 422 Unprocessable Entity in the REST layer.
 *
 * @see com.order.demo.application.service.OrderPlacementSaga#reserveAllItems
 */
public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String sku) {
        super("Insufficient inventory for SKU: " + sku);
    }
}
