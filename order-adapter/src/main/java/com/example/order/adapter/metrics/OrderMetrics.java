package com.example.order.adapter.metrics;

import com.example.order.application.port.out.MetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed adapter for {@link MetricsPort}.
 *
 * <p>Exposes Prometheus-compatible metrics for monitoring the order
 * placement flow, saga execution, and inventory reservation outcomes.
 *
 * <p>Meters are looked up by name + tags via {@link MeterRegistry}, which
 * deduplicates internally — safe to call repeatedly with the same keys.
 *
 * <p>Metrics are prefixed with {@code orders.} or {@code saga.} and include
 * tags for status, SKU, step, and failure reasons where applicable.
 *
 * @see MetricsPort
 */
@Component
public class OrderMetrics implements MetricsPort {

    private static final String ORDERS_PLACED = "orders.placed";
    private static final String ORDERS_FAILED = "orders.failed";
    private static final String INVENTORY_RESERVATION = "inventory.reservation";
    private static final String SAGA_DURATION = "saga.duration";
    private static final String SAGA_STEP_DURATION = "saga.step.duration";
    private static final String SAGA_GAP_DURATION = "saga.gap.duration";

    private final MeterRegistry meterRegistry;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordOrderPlaced(String status) {
        meterRegistry.counter(ORDERS_PLACED, Tags.of("status", status)).increment();
    }

    @Override
    public void recordOrderFailed(String reason) {
        meterRegistry.counter(ORDERS_FAILED, Tags.of("reason", reason)).increment();
    }

    @Override
    public void recordInventoryReservation(String sku, boolean success) {
        meterRegistry.counter(INVENTORY_RESERVATION,
                Tags.of("sku", sku, "result", success ? "success" : "failure")).increment();
    }

    @Override
    public void recordSagaDuration(long durationMillis, String outcome) {
        Timer.builder(SAGA_DURATION)
                .tag("outcome", outcome)
                .description("End-to-end duration of a single order placement saga")
                .register(meterRegistry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordSagaStepDuration(String step, long durationMillis, String outcome) {
        Timer.builder(SAGA_STEP_DURATION)
                .tag("step", step)
                .tag("outcome", outcome)
                .description("Duration of a single saga step")
                .register(meterRegistry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordSagaGap(String gap, long durationMillis) {
        Timer.builder(SAGA_GAP_DURATION)
                .tag("gap", gap)
                .description("Idle time between two saga phases")
                .register(meterRegistry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }
}
