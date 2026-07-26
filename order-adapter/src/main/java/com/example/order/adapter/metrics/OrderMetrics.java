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

    private static final String METRIC_ORDERS_PLACED = "orders.placed";
    private static final String METRIC_ORDERS_FAILED = "orders.failed";
    private static final String METRIC_INVENTORY_RESERVATION = "inventory.reservation";
    private static final String METRIC_SAGA_DURATION = "saga.duration";
    private static final String METRIC_SAGA_STEP_DURATION = "saga.step.duration";
    private static final String METRIC_SAGA_GAP_DURATION = "saga.gap.duration";

    private final BusinessMetricsPort metrics;

    public OrderMetrics(BusinessMetricsPort metrics) {
        this.metrics = metrics;
    }

    @Override
    public void recordOrderPlaced(String status) {
        metrics.increment(METRIC_ORDERS_PLACED, "status", status);
    }

    @Override
    public void recordOrderFailed(String reason) {
        metrics.increment(METRIC_ORDERS_FAILED, "reason", reason);
    }

    @Override
    public void recordInventoryReservation(String sku, boolean success) {
        metrics.increment(METRIC_INVENTORY_RESERVATION, "sku", sku, "result", success ? "success" : "failure");
    }

    @Override
    public void recordSagaDuration(long durationMillis, String outcome) {
        metrics.timer(METRIC_SAGA_DURATION, "End-to-end duration of a single order placement saga",
                "outcome", outcome).record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordSagaStepDuration(String step, long durationMillis, String outcome) {
        metrics.timer(METRIC_SAGA_STEP_DURATION, "Duration of a single saga step",
                "step", step, "outcome", outcome).record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordSagaGap(String gap, long durationMillis) {
        metrics.timer(METRIC_SAGA_GAP_DURATION, "Idle time between two saga phases",
                "gap", gap).record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
