package com.order.demo.application.port.in;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderSearchCriteriaTest {

    @Test
    @DisplayName("valid page and size should pass through unchanged")
    void testValidPageAndSizePassThroughUnchanged() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "cust-1", "CREATED", null, null, 2, 10);

        assertEquals(2, criteria.page(), "page should remain unchanged when non-negative");
        assertEquals(10, criteria.size(), "size should remain unchanged when in valid range 1..100");
        assertEquals("cust-1", criteria.customerId());
        assertEquals("CREATED", criteria.status());
    }

    @Test
    @DisplayName("negative page should be clamped to zero")
    void testNegativePageClampedToZero() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                null, null, null, null, -1, 10);

        assertEquals(0, criteria.page(), "negative page must be clamped to 0");
    }

    @Test
    @DisplayName("zero size should be clamped to default 20")
    void testZeroSizeClampedToDefault() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                null, null, null, null, 0, 0);

        assertEquals(20, criteria.size(), "size <= 0 must be clamped to default 20");
    }

    @Test
    @DisplayName("negative size should be clamped to default 20")
    void testNegativeSizeClampedToDefault() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                null, null, null, null, 0, -5);

        assertEquals(20, criteria.size(), "negative size must be clamped to default 20");
    }

    @Test
    @DisplayName("size exceeding 100 should be clamped to maximum 100")
    void testOversizedClampedToMaximum() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                null, null, null, null, 0, 500);

        assertEquals(100, criteria.size(), "size > 100 must be clamped to maximum 100");
    }

    @Test
    @DisplayName("boundary size 100 should pass through unchanged")
    void testBoundarySizeHundredPassThrough() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                null, null, null, null, 0, 100);

        assertEquals(100, criteria.size(), "size exactly 100 is valid and unchanged");
    }
}
