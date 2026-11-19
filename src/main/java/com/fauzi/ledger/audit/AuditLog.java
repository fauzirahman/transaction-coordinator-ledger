package com.fauzi.ledger.audit;

import com.fauzi.ledger.transfer.Transfer;
import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, length = 100)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(
            Transfer transfer,
            UUID actorUserId,
            String action,
            Map<String, Object> details
    ) {
        this.transfer = transfer;
        this.actorUserId = actorUserId;
        this.action = action;
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
