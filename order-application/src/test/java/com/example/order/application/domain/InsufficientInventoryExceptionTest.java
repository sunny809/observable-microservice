package com.order.demo.application.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InsufficientInventoryExceptionTest {

    @Test
    void testMessageContainsSku() {
        InsufficientInventoryException ex = new InsufficientInventoryException("SKU-999");
        assertTrue(ex.getMessage().contains("SKU-999"));
    }

    @Test
    void testExtendsRuntimeException() {
        InsufficientInventoryException ex = new InsufficientInventoryException("SKU-1");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
