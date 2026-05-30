package com.example.order.adapter.outbound.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderEntityTest {

    @Test
    void testDefaultConstructorCreatesEmptyEntity() {
        OrderEntity entity = new OrderEntity();
        assertNull(entity.getId());
        assertNull(entity.getCustomerId());
        assertNull(entity.getIdempotencyKey());
        assertNull(entity.getReservationId());
        assertNull(entity.getStatus());
        assertNull(entity.getCreatedAt());
    }

    @Test
    void testFullConstructorStoresAllValues() {
        LocalDateTime now = LocalDateTime.now();
        OrderEntity entity = new OrderEntity("id-1", "cust-1", "idem-1", "resv-1", "CREATED", now);

        assertEquals("id-1", entity.getId());
        assertEquals("cust-1", entity.getCustomerId());
        assertEquals("idem-1", entity.getIdempotencyKey());
        assertEquals("resv-1", entity.getReservationId());
        assertEquals("CREATED", entity.getStatus());
        assertEquals(now, entity.getCreatedAt());
    }

    @Test
    void testSetStatusUpdatesStatus() {
        OrderEntity entity = new OrderEntity("id-1", "c1", "i1", "r1", "CREATED", LocalDateTime.now());
        assertEquals("CREATED", entity.getStatus());

        entity.setStatus("RESERVED");
        assertEquals("RESERVED", entity.getStatus());
    }

    @Test
    void testGetCreatedAtReturnsStoredValue() {
        LocalDateTime now = LocalDateTime.now();
        OrderEntity entity = new OrderEntity("id-1", "c1", "i1", "r1", "CREATED", now);
        assertEquals(now, entity.getCreatedAt());
    }
}
