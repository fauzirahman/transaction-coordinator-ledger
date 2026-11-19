package com.fauzi.ledger.account;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, length = 50)
    private String accountNumber;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Account() {
    }

    public Account(
            String accountNumber,
            UUID ownerUserId,
            String accountName,
            String currency,
            BigDecimal balance
    ) {
        this.accountNumber = accountNumber;
        this.ownerUserId = ownerUserId;
        this.accountName = accountName;
        this.currency = currency;
        this.balance = balance;
        this.status = "ACTIVE";
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getStatus() {
        return status;
    }

    public void debit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        this.balance = this.balance.add(amount);
    }
}

