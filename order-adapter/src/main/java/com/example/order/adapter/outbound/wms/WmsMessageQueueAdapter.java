package com.example.order.adapter.outbound.wms;

import com.example.order.application.port.out.WmsAck;
import com.example.order.application.port.out.WmsPort;
import com.example.order.application.port.out.WmsShipmentInstruction;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
public class WmsMessageQueueAdapter implements WmsPort {

    @Override
    public CompletableFuture<WmsAck> sendInstruction(WmsShipmentInstruction instruction) {
        // TODO: Implement message queue adapter for WMS instruction delivery
        return CompletableFuture.failedFuture(new UnsupportedOperationException("WMS queue adapter not implemented"));
    }
}
