package com.order.demo.application.port.in;

import java.util.List;

/**
 * Read model DTO for order detail with saga steps.
 */
public record OrderDetail(
    OrderSummary summary,
    List<SagaStepView> sagaSteps
) {}
