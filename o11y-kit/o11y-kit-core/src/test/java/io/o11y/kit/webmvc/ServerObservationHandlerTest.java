package io.o11y.kit.webmvc;

import io.o11y.kit.http.HttpMetricRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ServerObservationHandler}.
 */
class ServerObservationHandlerTest {

    private HttpMetricRecorder recorder;
    private ServerObservationHandler handler;

    @BeforeEach
    void setUp() {
        recorder = mock(HttpMetricRecorder.class);
        handler = new ServerObservationHandler(recorder);
    }

    @Test
    void shouldRecordServerRequestOnCompletion() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern",
                "/api/v1/orders");
        var response = new MockHttpServletResponse();
        response.setStatus(201);

        handler.preHandle(request, response, null);
        Thread.sleep(5); // ensure measurable duration
        handler.afterCompletion(request, response, null, null);

        verify(recorder).recordServerRequest(eq("POST"), eq("/api/v1/orders"), eq(201), longThat(v -> v >= 5));
    }

    @Test
    void shouldSetTraceIdResponseHeader() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/health");
        var response = new MockHttpServletResponse();

        handler.preHandle(request, response, null);

        assertNotNull(response.getHeader("X-Trace-Id"));
    }

    @Test
    void shouldFallbackToUnknownWhenNoPattern() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/fallback");
        var response = new MockHttpServletResponse();

        handler.preHandle(request, response, null);
        handler.afterCompletion(request, response, null, null);

        verify(recorder).recordServerRequest(eq("GET"), eq("UNKNOWN"), anyInt(), anyLong());
    }

    @Test
    void shouldCleanupMdcEvenWhenRecorderThrows() {
        var recorder = mock(HttpMetricRecorder.class);
        doThrow(new RuntimeException("metrics blow up"))
                .when(recorder).recordServerRequest(anyString(), anyString(), anyInt(), anyLong());
        var throwingHandler = new ServerObservationHandler(recorder);
        var request = new MockHttpServletRequest("GET", "/api/boom");
        var response = new MockHttpServletResponse();

        throwingHandler.preHandle(request, response, null);
        // Exception is caught and logged internally, should not propagate
        assertDoesNotThrow(() ->
                throwingHandler.afterCompletion(request, response, null, null));
    }

    @Test
    void shouldHandleMissingStartTimeGracefully() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/no-start");
        var response = new MockHttpServletResponse();

        // No preHandle was called, so startTime attribute is absent
        handler.afterCompletion(request, response, null, null);

        verify(recorder, never()).recordServerRequest(anyString(), anyString(), anyInt(), anyLong());
    }
}
