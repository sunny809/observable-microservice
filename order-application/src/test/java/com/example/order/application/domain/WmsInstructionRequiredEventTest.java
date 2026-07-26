package com.order.demo.application.domain;

import com.order.demo.application.port.out.WmsShipmentInstruction;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WmsInstructionRequiredEventTest {

    @Test
    void testConstructorStoresAllFields() {
        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        InventoryReservation reservation = InventoryReservation.pending("SKU-1", 1, "ord-1");
        WmsInstructionRequiredEvent event = new WmsInstructionRequiredEvent("ord-1", instruction, reservation);

        assertEquals("ord-1", event.getOrderId());
        assertEquals(instruction, event.getInstruction());
        assertEquals(reservation, event.getReservation());
        assertEquals(1, event.getReservations().size());
        assertEquals(reservation, event.getReservations().get(0));
    }

    @Test
    void testListConstructorStoresAllReservations() {
        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        InventoryReservation r1 = InventoryReservation.pending("SKU-1", 2, "ord-1");
        InventoryReservation r2 = InventoryReservation.pending("SKU-2", 3, "ord-1");
        WmsInstructionRequiredEvent event = new WmsInstructionRequiredEvent("ord-1", instruction, List.of(r1, r2));

        assertEquals(2, event.getReservations().size());
        assertEquals(r1, event.getReservation());
        assertEquals(r2, event.getReservations().get(1));
    }

    @Test
    void testConstructorRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () ->
                new WmsInstructionRequiredEvent("ord-1", new WmsShipmentInstruction("ord-1", "resv-1"), List.of()));
    }

    @Test
    void testConstructorRejectsNullOrderId() {
        assertThrows(NullPointerException.class, () ->
                new WmsInstructionRequiredEvent(null, new WmsShipmentInstruction("ord-1", "resv-1"), (InventoryReservation) InventoryReservation.pending("SKU-1", 1, "ord-1")));
    }

    @Test
    void testConstructorRejectsNullInstruction() {
        assertThrows(NullPointerException.class, () ->
                new WmsInstructionRequiredEvent("ord-1", null, (InventoryReservation) InventoryReservation.pending("SKU-1", 1, "ord-1")));
    }

    @Test
    void testConstructorRejectsNullReservation() {
        assertThrows(NullPointerException.class, () ->
                new WmsInstructionRequiredEvent("ord-1", new WmsShipmentInstruction("ord-1", "resv-1"), (InventoryReservation) null));
    }

    @Test
    void testConstructorRejectsNullReservationList() {
        assertThrows(NullPointerException.class, () ->
                new WmsInstructionRequiredEvent("ord-1", new WmsShipmentInstruction("ord-1", "resv-1"), (List<InventoryReservation>) null));
    }
}
