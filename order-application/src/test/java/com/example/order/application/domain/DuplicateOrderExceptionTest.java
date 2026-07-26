package com.order.demo.application.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateOrderExceptionTest {

    @Test
    void testMessageContainsIdempotencyKey() {
        DuplicateOrderException ex = new DuplicateOrderException("key-123");
        assertTrue(ex.getMessage().contains("key-123"));
    }

    @Test
    void testExtendsRuntimeException() {
        DuplicateOrderException ex = new DuplicateOrderException("key-1");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
