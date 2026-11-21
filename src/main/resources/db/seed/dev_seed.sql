DO $$
DECLARE
    seed_owner_id UUID;
BEGIN
    SELECT id
    INTO seed_owner_id
    FROM app_users
    ORDER BY created_at, id
    LIMIT 1;

    IF seed_owner_id IS NULL THEN
        RAISE EXCEPTION 'Register a user before loading development seed data';
    END IF;

    INSERT INTO accounts (
        id, account_number, owner_user_id, account_name, currency, balance, status
    )
    VALUES
        ('00000000-0000-4000-8000-000000000001', 'DUMMY-ACCOUNT-001',
         seed_owner_id, 'Dummy Account 01', 'USD', 1000.0000, 'ACTIVE'),
        ('00000000-0000-4000-8000-000000000002', 'DUMMY-ACCOUNT-002',
         seed_owner_id, 'Dummy Account 02', 'USD', 1000.0000, 'ACTIVE')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO transfers (
        from_account_id, to_account_id, amount, currency, reference,
        idempotency_key, status, failure_reason
    )
    SELECT
        CASE WHEN n % 2 = 1
            THEN '00000000-0000-4000-8000-000000000001'::UUID
            ELSE '00000000-0000-4000-8000-000000000002'::UUID
        END,
        CASE WHEN n % 2 = 1
            THEN '00000000-0000-4000-8000-000000000002'::UUID
            ELSE '00000000-0000-4000-8000-000000000001'::UUID
        END,
        10.0000 + n,
        'USD',
        'dummy-transfer-' || LPAD(n::TEXT, 2, '0'),
        'dummy-idempotency-' || LPAD(n::TEXT, 2, '0'),
        'FAILED',
        'Development seed record; no funds were moved.'
    FROM GENERATE_SERIES(1, 20) AS series(n)
    ON CONFLICT (reference) DO NOTHING;
END $$;
