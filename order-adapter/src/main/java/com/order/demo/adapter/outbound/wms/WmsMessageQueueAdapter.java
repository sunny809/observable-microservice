package com.order.demo.adapter.outbound.wms;

import com.order.demo.application.port.out.WmsAck;
import com.order.demo.application.port.out.WmsPort;
import com.order.demo.application.port.out.WmsShipmentInstruction;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Kafka-based adapter for sending WMS shipment instructions asynchronously.
 *
 * <p>Publishes {@link WmsShipmentInstruction} messages to a Kafka topic
 * for reliable delivery to the WMS service. Uses {@link KafkaTemplate} for
 * production-ready message publishing with acknowledgment.
 *
 * <p>The {@link WmsRestAdapter} is marked as {@link org.springframework.context.annotation.Primary}
 * to take precedence in most environments. This Kafka adapter is used when
 * asynchronous message-based communication is preferred.
 *
 * <p>Key configuration properties:
 * <ul>
 *   <li>{@code spring.kafka.bootstrap-servers} — Kafka broker addresses</li>
 *   <li>{@code app.kafka.wms.topic} — Topic name for WMS instructions</li>
 * </ul>
 *
 * @see WmsRestAdapter for HTTP-based WMS communication
 * @see org.springframework.kafka.core.KafkaTemplate
 */
@Component
public class WmsMessageQueueAdapter implements WmsPort {

    private static final Logger log = LoggerFactory.getLogger(WmsMessageQueueAdapter.class);
    private final KafkaTemplate<String, WmsShipmentInstruction> kafkaTemplate;
    private final String topic;
    private final String cancelTopic;

    public WmsMessageQueueAdapter(
            KafkaTemplate<String, WmsShipmentInstruction> kafkaTemplate,
            org.springframework.core.env.Environment env) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = env.getProperty("app.kafka.wms.topic", "wms.shipment.instructions");
        this.cancelTopic = env.getProperty("app.kafka.wms.cancel-topic", "wms.shipment.cancel");
    }

    /**
     * Sends a WMS shipment instruction to Kafka for asynchronous delivery.
     *
     * <p>The returned {@link CompletableFuture} completes when the message
     * is successfully acknowledged by Kafka. If the send fails, the future
     * completes exceptionally with the cause.
     *
     * @param instruction the shipment instruction containing order and reservation IDs
     * @return a future that completes when the message is acknowledged by Kafka
     */
    @Override
    public CompletableFuture<WmsAck> sendInstruction(WmsShipmentInstruction instruction) {
        CompletableFuture<WmsAck> result = new CompletableFuture<>();

        kafkaTemplate.send(topic, instruction.getOrderId(), instruction)
                .whenComplete((sendResult, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send WMS instruction to Kafka for order {}",
                                instruction.getOrderId(), ex);
                        result.completeExceptionally(ex);
                    } else {
                        log.info("WMS instruction sent to Kafka for order {}: offset={}",
                                instruction.getOrderId(),
                                sendResult.getRecordMetadata().offset());
                        result.complete(new WmsAck(true, String.valueOf(sendResult.getRecordMetadata().offset())));
                    }
                });

        return result;
    }

    /**
     * Voids a previously sent shipment instruction by publishing a cancel message
     * to the WMS cancel topic.
     */
    @Override
    public CompletableFuture<Void> cancelInstruction(WmsShipmentInstruction instruction) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        kafkaTemplate.send(cancelTopic, instruction.getOrderId(), instruction)
                .whenComplete((sendResult, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send WMS cancel to Kafka for order {}",
                                instruction.getOrderId(), ex);
                        result.completeExceptionally(ex);
                    } else {
                        log.info("WMS cancel sent to Kafka for order {}", instruction.getOrderId());
                        result.complete(null);
                    }
                });

        return result;
    }
}
