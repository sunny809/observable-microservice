package com.example.order.adapter.outbound.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SagaLogEntityTest {

    @Test
    @DisplayName("default constructor should create entity with null id")
    void testDefaultConstructorCreatesEmptyEntity() {
        SagaLogEntity entity = new SagaLogEntity();
        assertNull(entity.getId());
    }

    @Test
    @DisplayName("all-args constructor should store values and expose via getters")
    void testFullConstructorStoresAllValues() {
        LocalDateTime now = LocalDateTime.now();
        SagaLogEntity entity = new SagaLogEntity("ord-1", "ORDER_CREATED", "order saved", now);

        // Id is not set by constructor (GeneratedValue strategy)
        assertNull(entity.getId());
        assertEquals("ord-1", entity.getOrderId());
        assertEquals("ORDER_CREATED", entity.getStep());
        assertEquals("order saved", entity.getDetail());
        assertEquals(now, entity.getCreatedAt());
    }
}
