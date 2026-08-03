package com.order.demo.application.port.in;

import java.time.Instant;

/**
 * Read model DTO for a single saga step in order detail view.
 */
public record SagaStepView(
    String stepName,
    String stepStatus,
    Instant startedAt,
    Instant completedAt,
    String detail
) {}
