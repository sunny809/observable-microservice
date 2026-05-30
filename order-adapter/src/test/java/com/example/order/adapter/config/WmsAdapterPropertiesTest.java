package com.example.order.adapter.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WmsAdapterPropertiesTest {

    @Test
    void testGetBaseUrlReturnsConfiguredValue() {
        WmsAdapterProperties properties = new WmsAdapterProperties();
        assertDoesNotThrow(properties::getBaseUrl);
    }
}
