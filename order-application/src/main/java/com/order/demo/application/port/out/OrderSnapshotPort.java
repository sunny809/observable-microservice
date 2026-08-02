package com.order.demo.application.port.out;

import com.order.demo.application.domain.Order;
import com.order.demo.application.port.in.OrderSummary;
import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for order state snapshots.
 *
 * <p>Enables point-in-time queries: "what was the state of order X at time T?"
 * Snapshots are taken after each significant state transition in the saga.
 */
public interface OrderSnapshotPort {

    /**
     * Saves a snapshot of the current order state.
     *
     * @param order  the order to snapshot
     * @param reason the trigger reason (e.g., "ORDER_CREATED", "WMS_ACKED")
     */
    void saveSnapshot(Order order, String reason);

    /**
     * Finds the order state at a specific point in time.
     *
     * @param orderId     the order ID
     * @param pointInTime the point in time
     * @return the order summary at that time, or empty if no snapshot exists
     */
    Optional<OrderSummary> findSnapshotAt(String orderId, Instant pointInTime);
}
