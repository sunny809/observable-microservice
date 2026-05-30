package com.example.order.adapter.outbound.wms;

import com.example.order.application.port.out.WmsAck;
import com.example.order.application.port.out.WmsShipmentInstruction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class WmsMessageQueueAdapterTest {

    @Test
    void testSendInstructionReturnsFailedFuture() {
        WmsMessageQueueAdapter adapter = new WmsMessageQueueAdapter();
        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");
        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
        assertTrue(ex.getCause().getMessage().contains("not implemented"));
    }
}
