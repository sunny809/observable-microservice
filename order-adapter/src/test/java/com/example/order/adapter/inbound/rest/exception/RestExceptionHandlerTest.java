package com.example.order.adapter.inbound.rest.exception;

import com.example.order.application.domain.DuplicateOrderException;
import com.example.order.application.domain.InsufficientInventoryException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;

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
}
