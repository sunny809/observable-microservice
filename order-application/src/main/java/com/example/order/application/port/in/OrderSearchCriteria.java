package com.order.demo.application.port.in;

import java.time.Instant;

/**
 * Criteria for searching orders in the read model.
 */
public record OrderSearchCriteria(
    String customerId,
    String status,
    Instant createdAfter,
    Instant createdBefore,
    int page,
    int size
) {
    public OrderSearchCriteria {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
    }
}
