package com.order.demo.application.port.in;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderItemTest {

    @Test
    void testConstructorStoresValues() {
        OrderItem item = new OrderItem("SKU-A", 3);
        assertEquals("SKU-A", item.getSku());
        assertEquals(3, item.getQuantity());
    }

    @Test
    void testConstructorRejectsNullSku() {
        assertThrows(NullPointerException.class, () -> new OrderItem(null, 1));
    }

    @Test
    void testConstructorRejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new OrderItem("SKU-1", 0));
    }

    @Test
    void testConstructorRejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new OrderItem("SKU-1", -1));
    }
}
