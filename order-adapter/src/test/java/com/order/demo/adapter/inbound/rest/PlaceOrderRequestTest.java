package com.order.demo.adapter.inbound.rest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderRequestTest {

    private Validator validator;
    private ValidatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void testValidRequestPassesValidation() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setCustomerId("cust-1");
        request.setIdempotencyKey("idem-1");

        PlaceOrderRequest.OrderItemRequest item = new PlaceOrderRequest.OrderItemRequest();
        item.setSku("SKU-1");
        item.setQuantity(5);
        request.setItems(java.util.List.of(item));

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testBlankCustomerIdFailsValidation() {
        PlaceOrderRequest request = buildValidRequest();
        request.setCustomerId("");

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerId")));
    }

    @Test
    void testNullCustomerIdFailsValidation() {
        PlaceOrderRequest request = buildValidRequest();
        request.setCustomerId(null);

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testEmptyItemsListFailsValidation() {
        PlaceOrderRequest request = buildValidRequest();
        request.setItems(java.util.List.of());

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("items")));
    }

    @Test
    void testNullItemsFailsValidation() {
        PlaceOrderRequest request = buildValidRequest();
        request.setItems(null);

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testBlankSkuInItemFailsValidation() {
        PlaceOrderRequest request = buildValidRequest();
        PlaceOrderRequest.OrderItemRequest item = request.getItems().get(0);
        item.setSku("");

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("items[0].sku")));
    }

    @Test
    void testGettersAndSettersWorkCorrectly() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setCustomerId("cust-1");
        assertEquals("cust-1", request.getCustomerId());

        request.setIdempotencyKey("idem-1");
        assertEquals("idem-1", request.getIdempotencyKey());

        PlaceOrderRequest.OrderItemRequest item = new PlaceOrderRequest.OrderItemRequest();
        item.setSku("SKU-1");
        item.setQuantity(10);
        request.setItems(java.util.List.of(item));

        assertEquals(1, request.getItems().size());
        assertEquals("SKU-1", request.getItems().get(0).getSku());
        assertEquals(10, request.getItems().get(0).getQuantity());
    }

    @Test
    void testZeroQuantityFailsValidation() {
        PlaceOrderRequest request = buildValidRequest();
        request.getItems().get(0).setQuantity(0);

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("items[0].quantity")));
    }

    @Test
    void testNegativeQuantityFailsValidation() {
        PlaceOrderRequest request = buildValidRequest();
        request.getItems().get(0).setQuantity(-1);

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("items[0].quantity")));
    }

    private PlaceOrderRequest buildValidRequest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setCustomerId("cust-1");
        request.setIdempotencyKey("idem-1");

        PlaceOrderRequest.OrderItemRequest item = new PlaceOrderRequest.OrderItemRequest();
        item.setSku("SKU-1");
        item.setQuantity(5);
        request.setItems(java.util.List.of(item));
        return request;
    }
}
