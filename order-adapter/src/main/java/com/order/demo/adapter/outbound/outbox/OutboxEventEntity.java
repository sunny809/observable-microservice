package com.order.demo.adapter.outbound.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    public OutboxEventEntity() {}

    public OutboxEventEntity(String aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
        this.retryCount = 0;
        this.maxRetries = 5;
    }

    // Getters and setters
    public Long getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public Integer getRetryCount() { return retryCount; }
    public Integer getMaxRetries() { return maxRetries; }

    public void setId(Long id) { this.id = id; }
    public void setStatus(String status) { this.status = status; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
}
