package com.order.demo.adapter.outbound.wms;

import com.order.demo.application.port.out.WmsAck;
import com.order.demo.application.port.out.WmsShipmentInstruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WmsMessageQueueAdapterTest {

    @Mock
    private KafkaTemplate<String, WmsShipmentInstruction> kafkaTemplate;

    @Mock
    private Environment env;

    @Test
    void testSendInstructionReturnsFailedFuture() {
        when(env.getProperty(anyString(), anyString())).thenReturn("wms.shipment.instructions");

        WmsMessageQueueAdapter adapter = new WmsMessageQueueAdapter(kafkaTemplate, env);
        WmsShipmentInstruction instruction = new WmsShipmentInstruction("ord-1", "resv-1");

        CompletableFuture<SendResult<String, WmsShipmentInstruction>> kafkaFuture = new CompletableFuture<>();
        kafkaFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(kafkaFuture);

        CompletableFuture<WmsAck> future = adapter.sendInstruction(instruction);

        assertTrue(future.isCompletedExceptionally());
    }
}
