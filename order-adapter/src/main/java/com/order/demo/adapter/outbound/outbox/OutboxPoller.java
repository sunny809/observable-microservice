package com.order.demo.adapter.outbound.outbox;

import com.order.demo.application.port.out.MetricsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox table for PENDING events and publishes them to Kafka.
 *
 * <p>Runs on a configurable fixed delay (default 5s). For each pending event:
 * <ol>
 *   <li>Send to Kafka synchronously (blocking with timeout)</li>
 *   <li>On success: mark as SENT</li>
 *   <li>On failure: increment retry_count; if max_retries exceeded, mark as FAILED</li>
 * </ol>
 *
 * <p>This component is only active when a {@link KafkaTemplate} bean is available,
 * so it is automatically disabled in test profiles that do not configure Kafka.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxEventJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MetricsPort metricsPort;
    private final String wmsTopic;

    public OutboxPoller(OutboxEventJpaRepository outboxRepository,
                        KafkaTemplate<String, String> kafkaTemplate,
                        MetricsPort metricsPort,
                        @Value("${app.kafka.wms.topic:wms.shipment.instructions}") String wmsTopic) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.metricsPort = metricsPort;
        this.wmsTopic = wmsTopic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:5000}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEventEntity> pending = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");

        if (!pending.isEmpty()) {
            metricsPort.recordOutboxPendingCount(pending.size());
        }

        for (OutboxEventEntity event : pending) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);

                event.setStatus("SENT");
                event.setSentAt(LocalDateTime.now());
                metricsPort.recordOutboxEventSent(event.getEventType());
                log.info("Outbox event sent: id={}, type={}, aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    event.setStatus("FAILED");
                    log.error("Outbox event failed permanently: id={}, type={}, retries={}",
                            event.getId(), event.getEventType(), event.getRetryCount(), e);
                } else {
                    log.warn("Outbox event send failed (retry {}/{}): id={}, type={}",
                            event.getRetryCount(), event.getMaxRetries(),
                            event.getId(), event.getEventType(), e);
                }
                metricsPort.recordOutboxEventFailed(event.getEventType());
            }
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "WmsInstructionRequiredEvent" -> wmsTopic;
            default -> wmsTopic; // fallback; extend as more event types are added
        };
    }
}
