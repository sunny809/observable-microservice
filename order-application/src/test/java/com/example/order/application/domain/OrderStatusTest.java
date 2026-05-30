package com.example.order.application.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusTest {

    @Test
    void testValuesReturnsAllFourConstants() {
        OrderStatus[] values = OrderStatus.values();
        assertEquals(4, values.length);
    }

    @Test
    void testValueOfReturnsCorrectEnum() {
        assertEquals(OrderStatus.CREATED, OrderStatus.valueOf("CREATED"));
        assertEquals(OrderStatus.RESERVED, OrderStatus.valueOf("RESERVED"));
        assertEquals(OrderStatus.WMS_ACKED, OrderStatus.valueOf("WMS_ACKED"));
        assertEquals(OrderStatus.REJECTED, OrderStatus.valueOf("REJECTED"));
    }

    @Test
    void testEachStatusHasExpectedName() {
        for (OrderStatus status : OrderStatus.values()) {
            assertNotNull(status.name());
            assertFalse(status.name().isEmpty());
        }
    }
}
