package com.order.demo.adapter.metrics;

import com.order.demo.application.port.out.MetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link MetricsPort} backed by Micrometer {@link MeterRegistry}.
 *
 * <p>Metrics are prefixed with {@code orders.} or {@code saga.} and include
 * tags for status, SKU, step, and failure reasons where applicable.
 *
 * @see MetricsPort
 */
@Component
public class OrderMetrics implements MetricsPort {

    private static final String METRIC_ORDERS_PLACED = "orders.placed";
    private static final String METRIC_ORDERS_FAILED = "orders.failed";
    private static final String METRIC_INVENTORY_RESERVATION = "inventory.reservation";
    private static final String METRIC_SAGA_DURATION = "saga.duration";
    private static final String METRIC_SAGA_STEP_DURATION = "saga.step.duration";
    private static final String METRIC_SAGA_GAP_DURATION = "saga.gap.duration";
    private static final String METRIC_OUTBOX_SENT = "outbox.events.sent";
    private static final String METRIC_OUTBOX_FAILED = "outbox.events.failed";
    private static final String METRIC_OUTBOX_PENDING = "outbox.events.pending";
    private static final String METRIC_LIFECYCLE_ARCHIVED = "lifecycle.rows.archived";
    private static final String METRIC_LIFECYCLE_DELETED = "lifecycle.rows.deleted";

    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordOrderPlaced(String status) {
        registry.counter(METRIC_ORDERS_PLACED, "status", status).increment();
    }

    @Override
    public void recordOrderFailed(String reason) {
        registry.counter(METRIC_ORDERS_FAILED, "reason", reason).increment();
    }

    @Override
    public void recordInventoryReservation(String sku, boolean success) {
        registry.counter(METRIC_INVENTORY_RESERVATION, "sku", sku, "result", success ? "success" : "failure").increment();
    }

    @Override
    public void recordSagaDuration(long durationMillis, String outcome) {
        Timer.builder(METRIC_SAGA_DURATION)
                .description("End-to-end duration of a single order placement saga")
                .tag("outcome", outcome)
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordSagaStepDuration(String step, long durationMillis, String outcome) {
        Timer.builder(METRIC_SAGA_STEP_DURATION)
                .description("Duration of a single saga step")
                .tag("step", step)
                .tag("outcome", outcome)
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordSagaGap(String gap, long durationMillis) {
        Timer.builder(METRIC_SAGA_GAP_DURATION)
                .description("Idle time between two saga phases")
                .tag("gap", gap)
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordOutboxEventSent(String eventType) {
        registry.counter(METRIC_OUTBOX_SENT, "eventType", eventType).increment();
    }

    @Override
    public void recordOutboxEventFailed(String eventType) {
        registry.counter(METRIC_OUTBOX_FAILED, "eventType", eventType).increment();
    }

    @Override
    public void recordOutboxPendingCount(long count) {
        registry.gauge(METRIC_OUTBOX_PENDING, count);
    }

    @Override
    public void recordLifecycleArchived(int count) {
        registry.counter(METRIC_LIFECYCLE_ARCHIVED).increment(count);
    }

    @Override
    public void recordLifecycleDeleted(int count) {
        registry.counter(METRIC_LIFECYCLE_DELETED).increment(count);
    }
}