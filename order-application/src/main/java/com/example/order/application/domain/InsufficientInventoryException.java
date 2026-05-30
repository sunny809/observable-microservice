package com.example.order.application.domain;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String sku) {
        super("Insufficient inventory for SKU: " + sku);
    }
}
