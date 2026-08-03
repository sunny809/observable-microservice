package com.order.demo.application.domain;

import com.order.demo.application.port.out.WmsShipmentInstruction;
import java.util.List;
import java.util.Objects;

/**
 * Domain event published when the WMS confirms that order picking is complete.
 *
 * <p>This event triggers the next phase of the saga: sending the TMS dispatch
 * instruction. It is published by {@link com.order.demo.application.service.OrderPlacementSaga}
 * and consumed by {@code @TransactionalEventListener}.
 *
 * <p>All fields are immutable (defensive copies are made for collections).
 *
 * @see com.order.demo.application.service.OrderPlacementSaga#onWmsPickingCompleted
 */
public class WmsPickingCompletedEvent {
    private final String orderId;
    private final String reservationId;
    private final List<InventoryReservation> reservations;

    /**
     * Creates a new event with multiple reservations.
     *
     * @param orderId the order ID
     * @param reservationId the primary reservation ID
     * @param reservations the list of inventory reservations
     */
    public WmsPickingCompletedEvent(String orderId,
                                    String reservationId,
                                    List<InventoryReservation> reservations) {
        this.orderId = Objects.requireNonNull(orderId);
        this.reservationId = Objects.requireNonNull(reservationId);
        this.reservations = List.copyOf(Objects.requireNonNull(reservations));
    }

    public String getOrderId() {
        return orderId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public List<InventoryReservation> getReservations() {
        return reservations;
    }
}
