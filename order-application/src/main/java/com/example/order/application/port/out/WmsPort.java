package com.example.order.application.port.out;

import java.util.concurrent.CompletableFuture;

public interface WmsPort {
    CompletableFuture<WmsAck> sendInstruction(WmsShipmentInstruction instruction);
}
