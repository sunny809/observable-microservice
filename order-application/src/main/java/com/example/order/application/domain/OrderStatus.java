package com.order.demo.application.domain;

/**
 * Represents the lifecycle states of an order in the order placement saga.
 *
 * <p>Status transitions:
 * <pre>
 * CREATED → WMS_ACKED → WMS_PICKED → TMS_DISPATCHED
 *    ↓         ↓            ↓              ↓
 * REJECTED  REJECTED   TMS_REJECTED   (terminal)
 * </pre>
 *
 * @see OrderPlacementSaga
 */
public enum OrderStatus {
    /** Order persisted, inventory reserved. */
    CREATED,
    /** Inventory reserved (intermediate state, not currently used). */
    RESERVED,
    /** WMS accepted shipment instruction, inventory confirmed. */
    WMS_ACKED,
    /** WMS confirmed picking is complete. */
    WMS_PICKED,
    /** TMS accepted dispatch instruction. */
    TMS_DISPATCHED,
    /** TMS rejected or failed; inventory released. */
    TMS_REJECTED,
    /** WMS rejected or failed; inventory released. */
    REJECTED
}
