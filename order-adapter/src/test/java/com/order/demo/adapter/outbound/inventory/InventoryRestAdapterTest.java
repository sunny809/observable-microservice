package com.order.demo.adapter.outbound.inventory;

import com.order.demo.adapter.AbstractHttpAdapterTest;
import com.order.demo.adapter.config.WebClientConfig;
import com.order.demo.application.domain.InventoryReservation;
import com.order.demo.application.domain.ReservationStatus;
import com.order.demo.application.port.out.ConfirmReservationCommand;
import com.order.demo.application.port.out.ReservationRequest;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
    void testOccupyReturnsReservationOnSuccess() throws Exception {
        String responseBody = "{\"success\":true,\"reservationId\":\"res-123\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
                .addHeader("Content-Type: application/json"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        CompletableFuture<InventoryReservation> future = adapter.occupy(request);
        InventoryReservation result = future.get();

        assertNotNull(result);
        assertEquals("res-123", result.getReservationId());
        assertEquals(ReservationStatus.PENDING, result.getStatus());
        assertEquals("SKU-1", result.getSku());
        assertEquals(5, result.getQuantity());
    }

    @Test
    @DisplayName("occupy should return null when response indicates failure")
    void testOccupyReturnsNullOnFailedResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"success\":false,\"reservationId\":null}")
                .addHeader("Content-Type: application/json"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        CompletableFuture<InventoryReservation> future = adapter.occupy(request);
        InventoryReservation result = future.get();

        assertNull(result);
    }

    @Test
    @DisplayName("occupy should throw on 500 Internal Server Error")
    void testOccupyThrowsOnServerError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        CompletableFuture<InventoryReservation> future = adapter.occupy(request);
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    @DisplayName("occupy should throw on 404 Not Found")
    void testOccupyThrowsOnNotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        CompletableFuture<InventoryReservation> future = adapter.occupy(request);
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    @DisplayName("occupy should throw on 400 Bad Request")
    void testOccupyThrowsOnBadRequest() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("Bad Request"));

        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");
        CompletableFuture<InventoryReservation> future = adapter.occupy(request);
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    @DisplayName("release should send DELETE to inventory reserve endpoint")
    void testReleaseCallsDeleteEndpoint() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        CompletableFuture<Void> future = adapter.release("res-123");
        future.get();

        var request = mockWebServer.takeRequest();
        assertEquals("/api/inventory/reserve/res-123", request.getPath());
        assertEquals("DELETE", request.getMethod());
    }

    @Test
    @DisplayName("confirm should send POST to inventory confirm endpoint")
    void testConfirmCallsPostEndpoint() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        CompletableFuture<Void> future = adapter.confirm(new ConfirmReservationCommand("res-123"));
        future.get();

        var request = mockWebServer.takeRequest();
        assertEquals("/api/inventory/confirm", request.getPath());
        assertEquals("POST", request.getMethod());
    }

    @Test
    @DisplayName("handleOccupyFallback should throw with service unavailable message")
    void testHandleOccupyFallbackThrows() {
        ReservationRequest request = new ReservationRequest("SKU-1", 5, "ORD-1");

        CompletableFuture<InventoryReservation> future = adapter.handleOccupyFallback(request, new RuntimeException("timeout"));
        ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
        assertTrue(thrown.getCause().getMessage().contains("Inventory service unavailable"));
    }

    @Test
    @DisplayName("handleReleaseFallback should throw with release failed message")
    void testHandleReleaseFallbackThrows() {
        CompletableFuture<Void> future = adapter.handleReleaseFallback("res-123", new RuntimeException("timeout"));
        ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
        assertTrue(thrown.getCause().getMessage().contains("Inventory release failed"));
    }

    @Test
    @DisplayName("handleConfirmFallback should throw with confirmation failed message")
    void testHandleConfirmFallbackThrows() {
        ConfirmReservationCommand cmd = new ConfirmReservationCommand("res-123");

        CompletableFuture<Void> future = adapter.handleConfirmFallback(cmd, new RuntimeException("timeout"));
        ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
        assertTrue(thrown.getCause().getMessage().contains("Inventory confirmation failed"));
    }

    @Test
    @DisplayName("release should throw on 500 server error")
    void testReleaseThrowsOnServerError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        CompletableFuture<Void> future = adapter.release("res-123");
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    @DisplayName("release should throw on 404 not found")
    void testReleaseThrowsOnNotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        CompletableFuture<Void> future = adapter.release("res-123");
        assertThrows(ExecutionException.class, future::get);
    }
}
