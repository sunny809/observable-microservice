package com.example.order.application.port.out;

/**
 * Outbound port for recording business and saga observability metrics.
 *
 * <p>Keeps the application layer free of any metrics framework (Micrometer,
 * OpenTelemetry). The adapter layer implements this interface — currently
 * {@code com.example.order.adapter.metrics.OrderMetrics} backed by Micrometer —
 * and translates each call into Prometheus meters exposed at
 * {@code /actuator/prometheus}.
 *
 * <p>Metrics emitted by the default implementation:
 * <ul>
 *   <li>{@code orders.placed} (Counter, tag {@code status})</li>
 *   <li>{@code orders.failed} (Counter, tag {@code reason})</li>
 *   <li>{@code inventory.reservation} (Counter, tags {@code sku}, {@code result})</li>
 *   <li>{@code saga.duration} (Timer, tag {@code outcome})</li>
 *   <li>{@code saga.step.duration} (Timer, tags {@code step}, {@code outcome})</li>
 *   <li>{@code saga.gap.duration} (Timer, tag {@code gap})</li>
 * </ul>
 *
 * @see com.example.order.adapter.metrics.OrderMetrics
 */
public interface MetricsPort {

    /**
     * Records a successfully placed order.
     *
     * @param status the final order status (e.g., {@code "CREATED"}, {@code "WMS_ACKED"})
     */
    void recordOrderPlaced(String status);

    /**
     * Records a failed order placement with the reason.
     *
     * @param reason the failure reason (e.g., {@code "INSUFFICIENT_INVENTORY"},
     *               {@code "DUPLICATE_ORDER"})
     */
    void recordOrderFailed(String reason);

    /**
     * Records an inventory reservation attempt for a SKU.
     *
     * @param sku     the SKU being reserved
     * @param success whether the reservation succeeded
     */
    void recordInventoryReservation(String sku, boolean success);

    /**
     * Records the end-to-end duration of a single order placement saga.
     *
     * @param durationMillis the saga duration in milliseconds
     * @param outcome        the saga outcome ({@code "success"}, {@code "compensation"},
     *                       {@code "duplicate"}, {@code "failure"})
     */
    void recordSagaDuration(long durationMillis, String outcome);

    /**
     * Records the duration of a single saga step (e.g., WMS acknowledgement).
     *
     * @param step           the saga step name (e.g., {@code "WMS_ACKED"}, {@code "TMS_DISPATCHED"})
     * @param durationMillis the step duration in milliseconds
     * @param outcome        the step outcome ({@code "success"}, {@code "rejected"}, {@code "failure"})
     */
    void recordSagaStepDuration(String step, long durationMillis, String outcome);

    /**
     * Records idle time (a "gap") between two saga phases.
     *
     * @param gap            the gap name (e.g., {@code "POST_COMMIT_TO_WMS"},
     *                       {@code "WMS_ACKED_TO_PICKED"})
     * @param durationMillis the idle duration in milliseconds
     */
    void recordSagaGap(String gap, long durationMillis);
}
