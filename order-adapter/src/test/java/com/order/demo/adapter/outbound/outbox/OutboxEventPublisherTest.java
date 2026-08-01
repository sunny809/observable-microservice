package com.order.demo.adapter.outbound.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
class OutboxEventPublisherTest {

    private OutboxEventJpaRepository outboxRepository;
    private ApplicationEventPublisher springPublisher;
    private ObjectMapper objectMapper;
    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxEventJpaRepository.class);
        springPublisher = mock(ApplicationEventPublisher.class);
        objectMapper = new ObjectMapper();
        publisher = new OutboxEventPublisher(outboxRepository, springPublisher, objectMapper);
    }

    @Test
    void publishWritesToOutboxAndSpring() {
        TestEvent event = new TestEvent("order-123");

        publisher.publish(event);

        verify(outboxRepository).save(argThat(entity ->
                entity.getAggregateId().equals("order-123") &&
                entity.getEventType().equals("TestEvent") &&
                entity.getStatus().equals("PENDING")));
        verify(springPublisher).publishEvent(event);
    }

    @Test
    void saveWritesToOutboxOnly() {
        publisher.save("order-456", "CUSTOM_EVENT", "{\"key\":\"value\"}");

        verify(outboxRepository).save(argThat(entity ->
                entity.getAggregateId().equals("order-456") &&
                entity.getEventType().equals("CUSTOM_EVENT") &&
                entity.getPayload().equals("{\"key\":\"value\"}")));
        verifyNoInteractions(springPublisher);
    }

    // Simple test event with getOrderId()
    static class TestEvent {
        private final String orderId;
        TestEvent(String orderId) { this.orderId = orderId; }
        public String getOrderId() { return orderId; }
    }
}
