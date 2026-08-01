package com.order.demo.application.port.in;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderCommandTest {

    @Test
    void testConstructorStoresValues() {
        PlaceOrderCommand command = new PlaceOrderCommand("cust-1", List.of(new OrderItem("SKU-1", 2)), "idem-1");

        assertEquals("cust-1", command.getCustomerId());
        assertEquals(1, command.getItems().size());
        assertEquals("SKU-1", command.getItems().get(0).getSku());
        assertEquals(2, command.getItems().get(0).getQuantity());
        assertEquals("idem-1", command.getIdempotencyKey());
    }

    @Test
    void testConstructorRejectsNullCustomerId() {
        assertThrows(NullPointerException.class, () -> new PlaceOrderCommand(null, List.of(new OrderItem("SKU-1", 1)), "idem-1"));
    }

    @Test
    void testConstructorRejectsNullItems() {
        assertThrows(NullPointerException.class, () -> new PlaceOrderCommand("cust-1", null, "idem-1"));
    }

    @Test
    void testConstructorRejectsNullIdempotencyKey() {
        assertThrows(NullPointerException.class, () -> new PlaceOrderCommand("cust-1", List.of(new OrderItem("SKU-1", 1)), null));
    }
}
