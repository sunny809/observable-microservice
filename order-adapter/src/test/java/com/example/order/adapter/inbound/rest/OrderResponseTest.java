package com.order.demo.adapter.inbound.rest;

import com.order.demo.application.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderResponseTest {

    @Test
    void testConstructorStoresAllValues() {
        OrderResponse response = new OrderResponse("ord-123", OrderStatus.CREATED, "trace-abc");

        assertEquals("ord-123", response.getOrderId());
        assertEquals(OrderStatus.CREATED, response.getStatus());
        assertEquals("trace-abc", response.getTraceId());
    }

    @Test
    void testGetOrderIdReturnsStoredValue() {
        OrderResponse response = new OrderResponse("id-1", OrderStatus.RESERVED, "t-1");
        assertEquals("id-1", response.getOrderId());
    }

    @Test
    void testGetStatusReturnsStoredValue() {
        OrderResponse response = new OrderResponse("id-1", OrderStatus.WMS_ACKED, "t-1");
        assertEquals(OrderStatus.WMS_ACKED, response.getStatus());
    }

    @Test
    void testGetTraceIdReturnsStoredValue() {
        OrderResponse response = new OrderResponse("id-1", OrderStatus.CREATED, "trace-xyz");
        assertEquals("trace-xyz", response.getTraceId());
    }
}
