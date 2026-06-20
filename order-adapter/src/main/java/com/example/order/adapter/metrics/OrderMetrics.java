package com.example.order.adapter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom metrics recorder for order-related business operations.
 *
 * <p>Exposes Prometheus-compatible metrics for monitoring the order
 * placement flow, saga execution, and inventory reservation outcomes.
 *
 * <p>Metrics are prefixed with {@code orders.} and include tags for
 * status, SKU, and failure reasons where applicable.
 *
 * @see io.micrometer.core.instrument.MeterRegistry
 */
@Component
public class OrderMetrics {

    private static final String ORDERS_PLACED = "orders.placed";
    private static final String ORDERS_FAILED = "orders.failed";
    private static final String INVENTORY_RESERVATION = "inventory.reservation";
    private static final String SAGA_DURATION = "saga.duration";

    private final MeterRegistry meterRegistry;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a successfully placed order.
     *
     * @param status the final order status (e.g., CREATED, WMS_ACKED)
     */
    public void recordOrderPlaced(String status) {
        Counter.builder(ORDERS_PLACED)
                .tag("status", status)
                .description("Total number of orders placed")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records a failed order placement with the reason.
     *
     * @param reason the failure reason (e.g., INSUFFICIENT_INVENTORY, DUPLICATE_ORDER)
     */
    public void recordOrderFailed(String reason) {
        Counter.builder(ORDERS_FAILED)
                .tag("reason", reason)
                .description("Total number of failed order placements")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records an inventory reservation attempt.
     *
     * @param sku the SKU being reserved
     * @param success whether the reservation succeeded
     */
    public void recordInventoryReservation(String sku, boolean success) {
        Counter.builder(INVENTORY_RESERVATION)
                .tag("sku", sku)
                .tag("result", success ? "success" : "failure")
                .description("Inventory reservation attempts")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records the duration of a saga execution.
     *
     * @param durationMillis the duration in milliseconds
     * @param outcome the saga outcome (success, compensation, failure)
     */
    public void recordSagaDuration(long durationMillis, String outcome) {
        Timer.builder(SAGA_DURATION)
                .tag("outcome", outcome)
                .description("Duration of saga execution in milliseconds")
                .register(meterRegistry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }
}
