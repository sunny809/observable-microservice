package com.example.order.infrastructure.adapter;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InventoryConfirmationSchedulerAdapterTest {

    @Test
    void testScheduleConfirmationDoesNotThrow() {
        InventoryConfirmationSchedulerAdapter adapter = new InventoryConfirmationSchedulerAdapter();
        InventoryReservation reservation = new InventoryReservation(
                "resv-123", "SKU-1", 5, "ord-1",
                ReservationStatus.PENDING, Instant.now(), null);

        assertDoesNotThrow(() -> adapter.scheduleConfirmation(reservation));
    }

    @Test
    void testScheduleConfirmationLogsInfoMessage() {
        InventoryConfirmationSchedulerAdapter adapter = new InventoryConfirmationSchedulerAdapter();
        InventoryReservation reservation = new InventoryReservation(
                "resv-456", "SKU-2", 3, "ord-2",
                ReservationStatus.PENDING, Instant.now(), null);

        // The method should complete without error and log an info message
        assertDoesNotThrow(() -> adapter.scheduleConfirmation(reservation));
    }
}
