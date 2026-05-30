package com.example.order.application.port.out;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WmsShipmentInstructionTest {

    @Test
    void testConstructorStoresValues() {
        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        assertEquals("ord-1", instruction.getOrderId());
        assertEquals("resv-1", instruction.getReservationId());
    }

    @Test
    void testConstructorRejectsNullOrderId() {
        assertThrows(NullPointerException.class, () -> new WmsShipmentInstruction(null, "resv-1"));
    }

    @Test
    void testConstructorRejectsNullReservationId() {
        assertThrows(NullPointerException.class, () -> new WmsShipmentInstruction("ord-1", null));
    }
}
