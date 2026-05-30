package com.example.order.application.port.out;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReservationRequestTest {

    @Test
    void testConstructorStoresValues() {
        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        assertEquals("SKU-1", request.getSku());
        assertEquals(5, request.getQuantity());
        assertEquals("ORD-1", request.getOrderId());
    }

    @Test
    void testConstructorRejectsNullSku() {
        assertThrows(NullPointerException.class, () -> new ReservationRequest(null, 1, "ORD-1"));
    }

    @Test
    void testConstructorRejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new ReservationRequest("SKU-1", 0, "ORD-1"));
    }

    @Test
    void testConstructorRejectsNullOrderId() {
        assertThrows(NullPointerException.class, () -> new ReservationRequest("SKU-1", 1, null));
    }
}
