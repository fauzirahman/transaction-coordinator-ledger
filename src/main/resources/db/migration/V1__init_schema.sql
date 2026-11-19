-- =========================================
-- 1. ACCOUNTS
-- =========================================
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(50) NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_account_balance CHECK (balance >= 0),
    CONSTRAINT chk_account_status
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX idx_accounts_owner
    ON accounts(owner_user_id);


-- =========================================
-- 2. TRANSFERS
-- =========================================
CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_account_id UUID NOT NULL REFERENCES accounts(id),
    to_account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reference VARCHAR(100) NOT NULL UNIQUE,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transfer_amount CHECK (amount > 0),
    CONSTRAINT chk_transfer_accounts
        CHECK (from_account_id <> to_account_id),
    CONSTRAINT chk_transfer_status
        CHECK (status IN (
            'PENDING', 'PROCESSING', 'COMPLETED',
            'FAILED', 'COMPENSATING', 'COMPENSATED'
        ))
);

CREATE INDEX idx_transfers_from_account
    ON transfers(from_account_id);

CREATE INDEX idx_transfers_to_account
    ON transfers(to_account_id);


-- =========================================
-- 3. LEDGER ENTRIES
-- =========================================
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES transfers(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ledger_entry_type
        CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount CHECK (amount > 0)
);

CREATE INDEX idx_ledger_transfer
    ON ledger_entries(transfer_id);

CREATE INDEX idx_ledger_account
    ON ledger_entries(account_id, created_at);


-- =========================================
-- 4. SAGA LOG
-- =========================================
CREATE TABLE saga_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES transfers(id),
    step_name VARCHAR(50) NOT NULL,
    step_status VARCHAR(20) NOT NULL,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_saga_status
        CHECK (step_status IN (
            'PENDING', 'PROCESSING', 'COMPLETED',
            'FAILED', 'COMPENSATED'
        ))
);

CREATE INDEX idx_saga_transfer
    ON saga_log(transfer_id, created_at);


-- =========================================
-- 5. AUDIT LOG
-- =========================================
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID REFERENCES transfers(id),
    actor_user_id UUID,
    action VARCHAR(100) NOT NULL,
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_transfer
    ON audit_log(transfer_id);


-- =========================================
-- 6. TRANSACTIONAL OUTBOX
-- =========================================
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events(published, created_at);
