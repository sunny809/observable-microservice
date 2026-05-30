package com.example.order.application.port.out;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmReservationCommandTest {

    @Test
    void testConstructorStoresValue() {
        ConfirmReservationCommand command = new ConfirmReservationCommand("res-1");
        assertEquals("res-1", command.getReservationId());
    }

    @Test
    void testConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new ConfirmReservationCommand(null));
    }
}
