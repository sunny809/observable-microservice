package com.order.demo.application.port.out;

import java.util.Objects;

public class ConfirmReservationCommand {
    private final String reservationId;

    public ConfirmReservationCommand(String reservationId) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId is required");
    }

    public String getReservationId() {
        return reservationId;
    }
}
