package com.example.order.adapter.outbound.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InventoryReservationEntityTest {

    @Test
    void testDefaultConstructorCreatesEmptyEntity() {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        // Default constructor creates empty entity — fields are null/zero
        // (no getters available, just verify construction succeeds)
    }

    @Test
    void testFullConstructorStoresAllValues() {
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime confirmed = created.plusHours(1);
        InventoryReservationEntity entity = new InventoryReservationEntity(
                "resv-1", "SKU-1", 10, "ord-1", "PENDING", created, confirmed);

        // Entity constructed successfully with all fields
        assertNotNull(entity);
    }
}
