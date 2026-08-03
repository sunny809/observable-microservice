package com.order.demo.application.port.in;

import java.time.Instant;

/**
 * Read model DTO for order list/search results.
 */
public record OrderSummary(
    String orderId,
    String customerId,
    String status,
    Instant createdAt,
    int reservationCount,
    int totalQuantity,
    String lastSagaStep,
    String lastSagaStatus
) {}
