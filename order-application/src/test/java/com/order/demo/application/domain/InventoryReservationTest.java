package com.order.demo.application.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InventoryReservationTest {

    @Test
    void testPendingFactoryCreatesCorrectReservation() {
        InventoryReservation reservation = InventoryReservation.pending("SKU-123", 5, "ORD-1");

        assertNotNull(reservation.getReservationId());
        assertEquals("SKU-123", reservation.getSku());
        assertEquals(5, reservation.getQuantity());
        assertEquals("ORD-1", reservation.getOrderId());
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        assertNotNull(reservation.getCreatedTime());
        assertNull(reservation.getConfirmedTime());
    }

    @Test
    void testConfirmChangesStatusAndSetsConfirmedTime() {
        InventoryReservation reservation = InventoryReservation.pending("SKU-1", 1, "ORD-1");
        Instant before = Instant.now();
        reservation.confirm();

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertNotNull(reservation.getConfirmedTime());
    }

    @Test
    void testReleaseChangesStatusToReleased() {
        InventoryReservation reservation = InventoryReservation.pending("SKU-1", 1, "ORD-1");
        reservation.release();

        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
    }

    @Test
    void testPendingRejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                InventoryReservation.pending("SKU-1", 0, "ORD-1"));
    }

    @Test
    void testPendingRejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                InventoryReservation.pending("SKU-1", -1, "ORD-1"));
    }

    @Test
    void testConstructorRejectsNullReservationId() {
        assertThrows(NullPointerException.class, () ->
                new InventoryReservation(null, "SKU-1", 1, "ORD-1", ReservationStatus.PENDING, Instant.now(), null));
    }

    @Test
    void testConstructorRejectsNullSku() {
        assertThrows(NullPointerException.class, () ->
                new InventoryReservation("resv-1", null, 1, "ORD-1", ReservationStatus.PENDING, Instant.now(), null));
    }
}
