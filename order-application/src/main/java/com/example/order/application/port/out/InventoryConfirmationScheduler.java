package com.example.order.application.port.out;

import com.example.order.application.domain.InventoryReservation;

public interface InventoryConfirmationScheduler {
    void scheduleConfirmation(InventoryReservation reservation);
}
