package com.order.demo.adapter.inbound.rest.exception;

import com.order.demo.adapter.inbound.rest.OrderNotFoundException;
import com.order.demo.application.domain.DuplicateOrderException;
import com.order.demo.application.domain.InsufficientInventoryException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    @SuppressWarnings("unchecked")
    void testHandleDuplicateReturns409() {
        var response = handler.handleDuplicate(new DuplicateOrderException("key-123"));

        assertEquals(409, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").toString().contains("key-123"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testHandleInsufficientReturns422() {
        var response = handler.handleInsufficient(new InsufficientInventoryException("SKU-99"));

        assertEquals(422, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").toString().contains("SKU-99"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testHandleGenericReturns500WithTraceId() {
        MDC.put("traceId", "trace-abc");
        try {
            HttpServletRequest request = mock(HttpServletRequest.class);
            var response = handler.handleGeneric(new RuntimeException("oops"), request);

            assertEquals(500, response.getStatusCodeValue());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("Internal server error", body.get("error"));
            assertEquals("trace-abc", body.get("traceId"));
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testHandleGenericReturnsUnknownTraceIdWhenNull() {
        MDC.remove("traceId");
        HttpServletRequest request = mock(HttpServletRequest.class);
        var response = handler.handleGeneric(new RuntimeException("oops"), request);

        assertEquals(500, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("unknown", body.get("traceId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testHandleOrderNotFoundReturns404() {
        var response = handler.handleOrderNotFound(new OrderNotFoundException("ord-missing"));

        assertEquals(404, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").toString().contains("ord-missing"),
                "error message should carry the missing order ID for client diagnostics");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testHandleValidationReturns400WithFieldErrors() {
        // Simulate a @Valid failure with two field errors via a mocked binding result
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "placeOrderRequest");
        bindingResult.addError(new FieldError("placeOrderRequest", "customerId", "must not be blank"));
        bindingResult.addError(new FieldError("placeOrderRequest", "items", "must not be empty"));

        org.springframework.web.bind.MethodArgumentNotValidException ex =
                mock(org.springframework.web.bind.MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        var response = handler.handleValidation(ex);

        assertEquals(400, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String error = body.get("error").toString();
        // Both field errors present, semicolon-separated
        assertTrue(error.contains("customerId: must not be blank"),
                "should report the customerId field error");
        assertTrue(error.contains("items: must not be empty"),
                "should report the items field error");
        assertTrue(error.contains("; "), "multiple errors should be semicolon-separated");
    }
}
