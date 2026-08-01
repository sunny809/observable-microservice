package com.order.demo.adapter.outbound.outbox;

import com.order.demo.application.port.out.DomainEventPublisher;
import com.order.demo.application.port.out.OutboxPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Replaces {@code SpringDomainEventPublisher}. Implements both
 * {@link OutboxPort} and {@link DomainEventPublisher}.
 *
 * <p>When {@link #publish(Object)} is called (by the saga), this class:
 * <ol>
 *   <li>Serializes the event to JSON and writes it to the outbox table
 *       (within the current transaction)</li>
 *   <li>Also publishes the event to Spring's ApplicationEventPublisher
 *       so that {@code @TransactionalEventListener} handlers still fire</li>
 * </ol>
 *
 * <p>The outbox write guarantees at-least-once delivery even if the
 * application crashes after commit. The OutboxPoller will re-send
 * any PENDING events on restart.
 */
@Component
public class OutboxEventPublisher implements OutboxPort, DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventJpaRepository outboxRepository;
    private final ApplicationEventPublisher springPublisher;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventJpaRepository outboxRepository,
                                ApplicationEventPublisher springPublisher,
                                ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.springPublisher = springPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String aggregateId, String eventType, String payload) {
        outboxRepository.save(new OutboxEventEntity(aggregateId, eventType, payload));
    }

    @Override
    public void publish(Object event) {
        // 1. Write to outbox (within current transaction)
        String eventType = event.getClass().getSimpleName();
        String aggregateId = extractAggregateId(event);
        String payload = serializeEvent(event);
        outboxRepository.save(new OutboxEventEntity(aggregateId, eventType, payload));

        // 2. Also publish to Spring so @TransactionalEventListener handlers fire
        springPublisher.publishEvent(event);
    }

    private String extractAggregateId(Object event) {
        try {
            var method = event.getClass().getMethod("getOrderId");
            return (String) method.invoke(event);
        } catch (Exception e) {
            log.warn("Could not extract orderId from event {}: {}", event.getClass().getSimpleName(), e.getMessage());
            return "unknown";
        }
    }

    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event {}: {}", event.getClass().getSimpleName(), e.getMessage());
            return "{}";
        }
    }
}
