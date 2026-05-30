package com.example.order.application.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReservationStatusTest {

    @Test
    void testValuesReturnsAllThreeConstants() {
        ReservationStatus[] values = ReservationStatus.values();
        assertEquals(3, values.length);
    }

    @Test
    void testValueOfReturnsCorrectEnum() {
        assertEquals(ReservationStatus.PENDING, ReservationStatus.valueOf("PENDING"));
        assertEquals(ReservationStatus.CONFIRMED, ReservationStatus.valueOf("CONFIRMED"));
        assertEquals(ReservationStatus.RELEASED, ReservationStatus.valueOf("RELEASED"));
    }

    @Test
    void testEachStatusHasExpectedName() {
        for (ReservationStatus status : ReservationStatus.values()) {
            assertNotNull(status.name());
            assertFalse(status.name().isEmpty());
        }
    }
}
