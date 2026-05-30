package com.example.order.application.domain;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String idempotencyKey) {
        super("Duplicate order request detected for key: " + idempotencyKey);
    }
}
