package com.example.order.adapter.inbound.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class TraceFilterTest {

    private final TraceFilter filter = new TraceFilter();

    @Test
    void testResolveTraceIdFromXb3TraceIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-B3-TraceId", "b3-trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("b3-trace-1", response.getHeader("X-Trace-Id"));
    }

    @Test
    void testResolveTraceIdFromTraceparentHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "traceparent-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("traceparent-1", response.getHeader("X-Trace-Id"));
    }

    @Test
    void testXb3TraceIdTakesPrecedenceOverTraceparent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-B3-TraceId", "b3-trace-1");
        request.addHeader("traceparent", "traceparent-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("b3-trace-1", response.getHeader("X-Trace-Id"));
    }

    @Test
    void testResolveTraceIdGeneratesUuidAsFallback() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceId = response.getHeader("X-Trace-Id");
        assertNotNull(traceId);
        assertTrue(traceId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
}
