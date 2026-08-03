package com.order.demo.application.port.out;

/**
 * Outbound port for writing events to the transactional outbox.
 *
 * <p>Events written to the outbox are guaranteed to be delivered
 * (at-least-once) by the {@code OutboxPoller}. This solves the
 * dual-write inconsistency problem between database commits and
 * message broker publishes.
 */
public interface OutboxPort {
    /**
     * Writes an event to the outbox table within the current transaction.
     *
     * @param aggregateId the aggregate ID (e.g., orderId)
     * @param eventType   the event type name (e.g., "WMS_INSTRUCTION_REQUIRED")
     * @param payload     the JSON-serialized event body
     */
    void save(String aggregateId, String eventType, String payload);
}
