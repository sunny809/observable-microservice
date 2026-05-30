package com.example.order.application.port.in;

import com.example.order.application.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderPlacedResultTest {

    @Test
    void testConstructorStoresValues() {
        OrderPlacedResult result = new OrderPlacedResult("ord-1", OrderStatus.CREATED, "trace-abc");

        assertEquals("ord-1", result.getOrderId());
        assertEquals(OrderStatus.CREATED, result.getStatus());
        assertEquals("trace-abc", result.getTraceId());
    }

    @Test
    void testConstructorRejectsNullOrderId() {
        assertThrows(NullPointerException.class, () -> new OrderPlacedResult(null, OrderStatus.CREATED, "trace-1"));
    }

    @Test
    void testConstructorAcceptsNullTraceId() {
        OrderPlacedResult result = new OrderPlacedResult("ord-1", OrderStatus.CREATED, null);
        assertNull(result.getTraceId());
    }
}
