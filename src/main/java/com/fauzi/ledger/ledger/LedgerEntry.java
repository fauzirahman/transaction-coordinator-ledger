package com.fauzi.ledger.ledger;

import com.fauzi.ledger.account.Account;
import com.fauzi.ledger.transfer.Transfer;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "entry_type", nullable = false, length = 10)
    private String entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(
            Transfer transfer,
            Account account,
            String entryType,
            BigDecimal amount,
            String currency,
            String description
    ) {
        if (!"DEBIT".equals(entryType) && !"CREDIT".equals(entryType)) {
            throw new IllegalArgumentException("Invalid ledger entry type");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        this.transfer = transfer;
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public Account getAccount() {
        return account;
    }

    public String getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
