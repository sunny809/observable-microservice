package com.order.demo.application.port.out;

import com.order.demo.application.domain.InventoryReservation;

public interface InventoryConfirmationScheduler {
    void scheduleConfirmation(InventoryReservation reservation);
}
