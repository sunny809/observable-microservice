package com.order.demo.adapter.outbound.wms;

import com.order.demo.adapter.AbstractHttpAdapterTest;
import com.order.demo.application.port.out.WmsAck;
import com.order.demo.application.port.out.WmsShipmentInstruction;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Tag("mock-web-server")
class WmsRestAdapterTest extends AbstractHttpAdapterTest {

    private WmsRestAdapter adapter;

    @BeforeEach
    void setUpAdapter() {
        WebClient webClient = WebClient.builder().baseUrl(baseUrl()).build();
        adapter = new WmsRestAdapter(webClient);
    }

    @Test
    @DisplayName("sendInstruction should return accepted ack on 200 success")
    void testSendInstructionReturnsAckOnSuccess() throws ExecutionException, InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"accepted\":true,\"messageId\":\"msg-1\"}")
                .addHeader("Content-Type: application/json"));

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);
        WmsAck ack = future.get();

        assertNotNull(ack);
        assertTrue(ack.isAccepted());
        assertEquals("msg-1", ack.getMessageId());
    }

    @Test
    @DisplayName("sendInstruction should return rejected ack when WMS rejects")
    void testSendInstructionReturnsRejectedAck() throws ExecutionException, InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"accepted\":false,\"messageId\":\"msg-2\"}")
                .addHeader("Content-Type: application/json"));

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);
        WmsAck ack = future.get();

        assertFalse(ack.isAccepted());
        assertEquals("msg-2", ack.getMessageId());
    }

    @Test
    @DisplayName("sendInstruction should fail future on 500 error")
    void testSendInstructionReturnsFailedFutureOnException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    @DisplayName("handleWmsFallback should return failed future with service unavailable")
    void testFallbackReturnsFailedFuture() {
        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.handleWmsFallback(instruction, new RuntimeException("down"));

        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    @DisplayName("sendInstruction should fail future on malformed response")
    void testSendInstructionFailsOnMalformedResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("not-json")
                .addHeader("Content-Type: application/json"));

        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);

        assertThrows(ExecutionException.class, future::get);
    }
}
