package com.fauzi.ledger.outbox;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload
    ) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.published = false;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public boolean isPublished() {
        return published;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
