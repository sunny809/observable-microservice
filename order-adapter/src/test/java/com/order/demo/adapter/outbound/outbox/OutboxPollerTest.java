package com.order.demo.adapter.outbound.outbox;

import com.order.demo.application.port.out.MetricsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class OutboxPollerTest {

    private OutboxEventJpaRepository outboxRepository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private MetricsPort metricsPort;
    private OutboxPoller poller;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxEventJpaRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        metricsPort = mock(MetricsPort.class);
        poller = new OutboxPoller(outboxRepository, kafkaTemplate, metricsPort, "wms.shipment.instructions");
    }

    @Test
    void shouldSendPendingEventAndMarkAsSent() throws Exception {
        OutboxEventEntity event = new OutboxEventEntity("order-1", "WmsInstructionRequiredEvent", "{}");
        when(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        mock(SendResult.class)));

        poller.pollAndPublish();

        verify(kafkaTemplate).send("wms.shipment.instructions", "order-1", "{}");
        verify(metricsPort).recordOutboxEventSent("WmsInstructionRequiredEvent");
    }

    @Test
    void shouldIncrementRetryOnFailure() throws Exception {
        OutboxEventEntity event = new OutboxEventEntity("order-1", "WmsInstructionRequiredEvent", "{}");
        when(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failed);

        poller.pollAndPublish();

        verify(metricsPort).recordOutboxEventFailed("WmsInstructionRequiredEvent");
    }

    @Test
    void shouldMarkAsFailedAfterMaxRetries() throws Exception {
        OutboxEventEntity event = new OutboxEventEntity("order-1", "WmsInstructionRequiredEvent", "{}");
        // Simulate event already at max retries - 1
        event.setRetryCount(4);
        when(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failed);

        poller.pollAndPublish();

        // After this failure, retryCount becomes 5 which equals maxRetries (5), so status should be FAILED
        assert event.getStatus().equals("FAILED");
        verify(metricsPort).recordOutboxEventFailed("WmsInstructionRequiredEvent");
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of());

        poller.pollAndPublish();

        verifyNoInteractions(kafkaTemplate);
        verifyNoInteractions(metricsPort);
    }
}
