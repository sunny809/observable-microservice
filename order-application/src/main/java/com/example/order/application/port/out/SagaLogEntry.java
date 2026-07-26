package com.order.demo.application.port.out;

import java.time.LocalDateTime;

public record SagaLogEntry(
    Long id,
    String orderId,
    String stepName,
    String stepStatus,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String detail
) {}