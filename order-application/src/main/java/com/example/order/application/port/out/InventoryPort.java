package com.example.order.application.port.out;

import com.example.order.application.domain.InventoryReservation;

public interface InventoryPort {
    InventoryReservation occupy(ReservationRequest request);

    void confirm(ConfirmReservationCommand request);

    void release(String reservationId);
}
