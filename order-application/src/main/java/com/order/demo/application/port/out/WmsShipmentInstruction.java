package com.order.demo.application.port.out;

import java.util.Objects;

public class WmsShipmentInstruction {
    private final String orderId;
    private final String reservationId;

    public WmsShipmentInstruction(String orderId, String reservationId) {
        this.orderId = Objects.requireNonNull(orderId, "orderId is required");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId is required");
    }

    public String getOrderId() {
        return orderId;
    }

    public String getReservationId() {
        return reservationId;
    }
}
