package com.example.order.adapter.metrics;

import com.example.order.application.port.out.MetricsPort;
import io.o11y.kit.metrics.BusinessMetricsPort;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link MetricsPort} backed by {@link BusinessMetricsPort}.
 *
 * <p>Delegates all metric recording to the o11y-kit {@link BusinessMetricsPort}
 * SPI, which is implemented by {@code MicrometerMetricsAdapter} and auto-configured
 * by the o11y-kit Spring Boot starter.
 *
 * <p>Metrics are prefixed with {@code orders.} or {@code saga.} and include
 * tags for status, SKU, step, and failure reasons where applicable.
 *
 * @see MetricsPort
 * @see BusinessMetricsPort
 */
@Component
public class OrderMetrics implements MetricsPort {

    private final BusinessMetricsPort metrics;

    public OrderMetrics(BusinessMetricsPort metrics) {
        this.metrics = metrics;
    }

    @Override
    public void recordOrderPlaced(String status) {
        metrics.increment("orders.placed", "status", status);
    }

    @Override
    public void recordOrderFailed(String reason) {
        metrics.increment("orders.failed", "reason", reason);
    }

    @Override
    public void recordInventoryReservation(String sku, boolean success) {
        metrics.increment("inventory.reservation", "sku", sku, "result", String.valueOf(success));
    }

    @Override
    public void recordSagaDuration(long durationMillis, String outcome) {
        metrics.recordDuration("saga.duration", durationMillis, "outcome", outcome);
    }

    @Override
    public void recordSagaStepDuration(String step, long durationMillis, String outcome) {
        metrics.recordDuration("saga.step.duration", durationMillis, "step", step, "outcome", outcome);
    }

    @Override
    public void recordSagaGap(String gap, long durationMillis) {
        metrics.recordDuration("saga.gap.duration", durationMillis, "gap", gap);
    }
}
