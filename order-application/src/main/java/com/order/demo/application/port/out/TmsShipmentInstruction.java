package com.order.demo.application.port.out;

import java.util.Objects;

/**
 * Immutable value object representing a TMS dispatch instruction.
 *
 * <p>Contains the information needed by the TMS to dispatch an order
 * after WMS picking is complete.
 *
 * @see TmsPort
 */
public class TmsShipmentInstruction {
    private final String orderId;
    private final String reservationId;

    public TmsShipmentInstruction(String orderId, String reservationId) {
        this.orderId = Objects.requireNonNull(orderId);
        this.reservationId = Objects.requireNonNull(reservationId);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getReservationId() {
        return reservationId;
    }
}
