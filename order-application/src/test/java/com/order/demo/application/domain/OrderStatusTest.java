package com.order.demo.application.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusTest {

    @Test
    void testValuesReturnsAllEightConstants() {
        OrderStatus[] values = OrderStatus.values();
        assertEquals(8, values.length);
    }

    @Test
    void testValueOfReturnsCorrectEnum() {
        assertEquals(OrderStatus.CREATED, OrderStatus.valueOf("CREATED"));
        assertEquals(OrderStatus.RESERVED, OrderStatus.valueOf("RESERVED"));
        assertEquals(OrderStatus.WMS_ACKED, OrderStatus.valueOf("WMS_ACKED"));
        assertEquals(OrderStatus.WMS_PICKED, OrderStatus.valueOf("WMS_PICKED"));
        assertEquals(OrderStatus.TMS_DISPATCHED, OrderStatus.valueOf("TMS_DISPATCHED"));
        assertEquals(OrderStatus.TMS_REJECTED, OrderStatus.valueOf("TMS_REJECTED"));
        assertEquals(OrderStatus.REJECTED, OrderStatus.valueOf("REJECTED"));
        assertEquals(OrderStatus.CANCELLED, OrderStatus.valueOf("CANCELLED"));
    }

    @Test
    void testEachStatusHasExpectedName() {
        for (OrderStatus status : OrderStatus.values()) {
            assertNotNull(status.name());
            assertFalse(status.name().isEmpty());
        }
    }
}
