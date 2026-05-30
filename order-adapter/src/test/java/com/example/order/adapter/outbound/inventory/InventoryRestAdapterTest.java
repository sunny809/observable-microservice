package com.example.order.adapter.outbound.inventory;

import com.example.order.adapter.AbstractHttpAdapterTest;
import com.example.order.adapter.config.WebClientConfig;
import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.ReservationStatus;
import com.example.order.application.port.out.ConfirmReservationCommand;
import com.example.order.application.port.out.ReservationRequest;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Tag("mock-web-server")
class InventoryRestAdapterTest extends AbstractHttpAdapterTest {

    private InventoryRestAdapter adapter;

    @BeforeEach
    void setUpAdapter() {
        WebClient webClient = WebClient.builder().baseUrl(baseUrl()).build();
        adapter = new InventoryRestAdapter(webClient);
    }

    @Test
    @DisplayName("occupy should return reservation on 200 success response")
    void testOccupyReturnsReservationOnSuccess() {
        String responseBody = "{\"success\":true,\"reservationId\":\"res-123\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
                .addHeader("Content-Type: application/json"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        InventoryReservation result = adapter.occupy(request);

        assertNotNull(result);
        assertEquals("res-123", result.getReservationId());
        assertEquals(ReservationStatus.PENDING, result.getStatus());
        assertEquals("SKU-1", result.getSku());
        assertEquals(5, result.getQuantity());
    }

    @Test
    @DisplayName("occupy should return null when response indicates failure")
    void testOccupyReturnsNullOnFailedResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"success\":false,\"reservationId\":null}")
                .addHeader("Content-Type: application/json"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        assertNull(adapter.occupy(request));
    }

    @Test
    @DisplayName("occupy should throw on 500 Internal Server Error")
    void testOccupyThrowsOnServerError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        assertThrows(RuntimeException.class, () -> adapter.occupy(request));
    }

    @Test
    @DisplayName("occupy should throw on 404 Not Found")
    void testOccupyThrowsOnNotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        assertThrows(RuntimeException.class, () -> adapter.occupy(request));
    }

    @Test
    @DisplayName("occupy should throw on 400 Bad Request")
    void testOccupyThrowsOnBadRequest() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("Bad Request"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        assertThrows(RuntimeException.class, () -> adapter.occupy(request));
    }

    @Test
    @DisplayName("release should send DELETE to inventory reserve endpoint")
    void testReleaseCallsDeleteEndpoint() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        adapter.release("res-123");

        var request = mockWebServer.takeRequest();
        assertEquals("/api/inventory/reserve/res-123", request.getPath());
        assertEquals("DELETE", request.getMethod());
    }

    @Test
    @DisplayName("confirm should send POST to inventory confirm endpoint")
    void testConfirmCallsPostEndpoint() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        adapter.confirm(new ConfirmReservationCommand("res-123"));

        var request = mockWebServer.takeRequest();
        assertEquals("/api/inventory/confirm", request.getPath());
        assertEquals("POST", request.getMethod());
    }

    @Test
    @DisplayName("handleOccupyFallback should throw with service unavailable message")
    void testHandleOccupyFallbackThrows() {
        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> adapter.handleOccupyFallback(request, new RuntimeException("timeout")));
        assertTrue(thrown.getMessage().contains("Inventory service unavailable"));
    }

    @Test
    @DisplayName("handleReleaseFallback should throw with release failed message")
    void testHandleReleaseFallbackThrows() {
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> adapter.handleReleaseFallback("res-123", new RuntimeException("timeout")));
        assertTrue(thrown.getMessage().contains("Inventory release failed"));
    }

    @Test
    @DisplayName("handleConfirmFallback should throw with confirmation failed message")
    void testHandleConfirmFallbackThrows() {
        ConfirmReservationCommand cmd = new ConfirmReservationCommand("res-123");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> adapter.handleConfirmFallback(cmd, new RuntimeException("timeout")));
        assertTrue(thrown.getMessage().contains("Inventory confirmation failed"));
    }

    @Test
    @DisplayName("release should throw on 500 server error")
    void testReleaseThrowsOnServerError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        assertThrows(RuntimeException.class, () -> adapter.release("res-123"));
    }

    @Test
    @DisplayName("release should throw on 404 not found")
    void testReleaseThrowsOnNotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        assertThrows(RuntimeException.class, () -> adapter.release("res-123"));
    }
}
