package com.fauzi.ledger.saga;

import com.fauzi.ledger.transfer.Transfer;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saga_log")
public class SagaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Column(name = "step_name", nullable = false, length = 50)
    private String stepName;

    @Column(name = "step_status", nullable = false, length = 20)
    private String stepStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SagaLog() {
    }

    public SagaLog(Transfer transfer, String stepName) {
        this.transfer = transfer;
        this.stepName = stepName;
        this.stepStatus = "PENDING";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void markProcessing() {
        this.stepStatus = "PROCESSING";
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.stepStatus = "COMPLETED";
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.stepStatus = "FAILED";
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void markCompensated() {
        this.stepStatus = "COMPENSATED";
        this.completedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public String getStepName() {
        return stepName;
    }

    public String getStepStatus() {
        return stepStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
