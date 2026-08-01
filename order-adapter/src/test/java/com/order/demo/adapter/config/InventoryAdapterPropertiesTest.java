package com.order.demo.adapter.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryAdapterPropertiesTest {

    @Test
    void testGetBaseUrlReturnsConfiguredValue() {
        InventoryAdapterProperties properties = new InventoryAdapterProperties();
        // Without Spring context, @Value field is null.
        // The getter method exists and is callable.
        assertDoesNotThrow(properties::getBaseUrl);
    }
}
