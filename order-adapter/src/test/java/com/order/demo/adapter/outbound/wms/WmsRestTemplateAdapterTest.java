package com.order.demo.adapter.outbound.wms;

import com.order.demo.application.port.out.WmsAck;
import com.order.demo.application.port.out.WmsShipmentInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Tests for {@link WmsRestTemplateAdapter} using Spring's {@link MockRestServiceServer}.
 *
 * <p>Verifies the synchronous RestTemplate-based WMS adapter correctly handles
 * success, rejection, server errors, and circuit breaker fallback scenarios.
 * The observation interceptor is not added in these unit tests (no Spring context),
 * so metrics/tracing verification is handled in integration tests.
 */
@Tag("integration")
class WmsRestTemplateAdapterTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private WmsRestTemplateAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        adapter = new WmsRestTemplateAdapter(restTemplate);
    }

    @Test
    @DisplayName("sendInstruction should return accepted ack on 200 success")
    void testSendInstructionReturnsAckOnSuccess() throws ExecutionException, InterruptedException {
        mockServer.expect(requestTo("/api/wms/shipments"))
                .andRespond(withSuccess(
                        "{\"accepted\":true,\"messageId\":\"msg-1\"}",
                        MediaType.APPLICATION_JSON));

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);
        WmsAck ack = future.get();

        assertNotNull(ack);
        assertTrue(ack.isAccepted());
        assertEquals("msg-1", ack.getMessageId());
        mockServer.verify();
    }

    @Test
    @DisplayName("sendInstruction should return rejected ack when WMS rejects")
    void testSendInstructionReturnsRejectedAck() throws ExecutionException, InterruptedException {
        mockServer.expect(requestTo("/api/wms/shipments"))
                .andRespond(withSuccess(
                        "{\"accepted\":false,\"messageId\":\"msg-2\"}",
                        MediaType.APPLICATION_JSON));

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);
        WmsAck ack = future.get();

        assertFalse(ack.isAccepted());
        assertEquals("msg-2", ack.getMessageId());
        mockServer.verify();
    }

    @Test
    @DisplayName("sendInstruction should fail future on 500 error")
    void testSendInstructionReturnsFailedFutureOnError() {
        mockServer.expect(requestTo("/api/wms/shipments"))
                .andRespond(withServerError());

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);

        assertThrows(ExecutionException.class, future::get);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendInstruction should return ack with false on null response body")
    void testSendInstructionHandlesNullBody() throws ExecutionException, InterruptedException {
        mockServer.expect(requestTo("/api/wms/shipments"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);
        WmsAck ack = future.get();

        assertNotNull(ack);
        assertFalse(ack.isAccepted());
        assertNull(ack.getMessageId());
        mockServer.verify();
    }

    @Test
    @DisplayName("handleWmsFallback should return failed future with service unavailable")
    void testFallbackReturnsFailedFuture() {
        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.handleWmsFallback(instruction, new RuntimeException("down"));

        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    @DisplayName("sendInstruction should send request with correct content type")
    void testSendInstructionSendsCorrectContentType() throws ExecutionException, InterruptedException {
        mockServer.expect(requestTo("/api/wms/shipments"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(
                        "{\"accepted\":true,\"messageId\":\"msg-3\"}",
                        MediaType.APPLICATION_JSON));

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-2", "resv-2");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);
        WmsAck ack = future.get();

        assertTrue(ack.isAccepted());
        mockServer.verify();
    }
}
