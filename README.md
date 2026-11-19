# Transaction Coordinator & Ledger Service

Spring Boot service for idempotent transfers, a PostgreSQL double-entry ledger,
and an orchestrated saga processed through RabbitMQ.

## Run with Docker

Set a private 256-bit-or-longer Base64 JWT HMAC key, then start the application
and its dependencies:

```bash
export JWT_SECRET_BASE64="$(openssl rand -base64 32)"
export BOOTSTRAP_ADMIN_USERNAME="ledger-admin"
export BOOTSTRAP_ADMIN_PASSWORD="replace-with-a-long-random-password"
docker compose up --build
```

The API listens on `http://localhost:8080`; PostgreSQL and RabbitMQ ports are
also exposed for local development. The JWT key is required and must be kept
secret. For production, use externally managed credentials, TLS, a distributed
rate limiter, and appropriate database/broker network controls.

The optional bootstrap variables create the first `ADMIN` account. Configure
both or neither; the password must be 12–72 UTF-8 bytes. If omitted, public
registration can create `USER` accounts, but an admin must be provisioned
separately.

## Authentication

Public registration creates only the `USER` role. Login returns an HS256 JWT
signed with `JWT_SECRET_BASE64`; access tokens expire after one hour. Tokens
contain a UUID `sub` and a `roles` array. `USER` can access only accounts they
own; `ADMIN` can access all accounts.

Register a user:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"a-long-unique-password"}'
```

Exchange the credentials for a JWT:

```bash
curl -X POST http://localhost:8080/api/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"a-long-unique-password"}'
```

Use the returned `access_token` as `Authorization: Bearer <token>` on protected
API calls. Usernames are case-insensitive; passwords are stored as BCrypt
hashes. Do not reuse the development signing key or bootstrap password in
production.

For example, provision an account through an approved onboarding process or
directly in a development database:

```sql
INSERT INTO accounts
    (account_number, owner_user_id, account_name, currency, balance, status)
VALUES
    ('A-10001', '11111111-1111-1111-1111-111111111111',
     'Example account', 'USD', 250.0000, 'ACTIVE');
```

Account provisioning is intentionally outside this transfer API. Use the
registered user's returned UUID as `owner_user_id`.

## API

All `/api/**` endpoints except `/api/auth/**` require a JWT. API requests are
limited to 60 per source IP per minute by default; configure `RATE_LIMIT_REQUESTS` and
`RATE_LIMIT_WINDOW` as needed. This in-memory limit is per application
instance, not a distributed production limiter.

### Create a transfer

`POST /api/transfers` requires `Idempotency-Key` (1–100 characters). Both
camelCase and the specified snake_case account field names are accepted.

```bash
curl -X POST http://localhost:8080/api/transfers \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: payment-2026-0001' \
  -d '{
    "from_account_id": "11111111-1111-1111-1111-111111111111",
    "to_account_id": "22222222-2222-2222-2222-222222222222",
    "amount": 10.2500,
    "currency": "USD",
    "reference": "invoice-1001"
  }'
```

Returns `202 Accepted` with a transfer UUID and initial `PENDING` status.
Requests are durably written to the transactional outbox and then published
to RabbitMQ. Processing is asynchronous.

### Read endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/transfers/{id}` | Transfer details and ledger entries |
| `GET` | `/api/transfers/reference/{reference}` | Lookup by unique reference |
| `GET` | `/api/accounts/{id}/balance` | Current account balance and currency |
| `GET` | `/api/accounts/{id}/ledger` | Account ledger entries |
| `GET` | `/api/saga/log/{transfer_id}` | Saga step history |

Authentication endpoints: `POST /api/auth/register` creates a USER and
`POST /api/auth/token` validates credentials and returns a JWT.

Users may read a transfer if they own either account involved. Account balance
and ledger endpoints require ownership. Admins may read across accounts.
Unauthorized and nonexistent resources both return `404` to avoid exposing
account or transfer existence.

## Consistency and concurrency

Each successful ledger posting inserts one `DEBIT` and one `CREDIT` entry,
updates both account balances, and verifies that the per-transfer debit and
credit totals match before committing the database transaction. Any failure
before commit rolls back both legs. A failure in a later saga step triggers
compensating ledger entries; ledger entries are never deleted or edited.

The service uses PostgreSQL pessimistic write locks (`SELECT ... FOR UPDATE`
through JPA `PESSIMISTIC_WRITE`) on both accounts, acquired in UUID order. A
ledger posting can affect the same hot accounts concurrently, so waiting and
serializing the balance check/update avoids lost updates and overdrafts. The
unique transfer reference and idempotency key are also enforced by database
constraints. Repeating the same request with the same key returns the existing
transfer; reusing the key for different transfer details returns `409`.

**Pessimistic vs. optimistic locking:** pessimistic locking takes a database
row lock before reading/updating a row (`SELECT FOR UPDATE`). It is useful for
ledger balances with frequent contention, where conflicts must not allow
overdrafts and retries are undesirable. Optimistic locking uses a version
column and detects conflicting writes at commit; it is efficient when
collisions are rare, but a payment system must retry the entire operation
safely after a conflict. For this service, pessimistic locking is chosen for
the account rows; the lock order is stable to reduce deadlocks.

## Saga, idempotency, and distributed transactions

The transfer saga is **orchestrated** by the service and has four logged
steps: debit, credit, notify, and audit. Debit and credit are separate saga
steps but their ledger entries and balance updates share one database
transaction, preserving the double-entry invariant. The outbox publishes
transfer requests and completion/compensation notifications at least once;
processing is idempotent, so redelivered messages do not post a second
ledger transaction.

In orchestration, a coordinator directs each step and compensation; it is
straightforward to trace, audit, and recover. In choreography, services react
to each other's events without a central coordinator; it reduces central
coupling but makes the overall flow and failure handling harder to observe.
This project uses orchestration because a financial transfer needs explicit
ordering, persisted step status, and a clear compensation path.

An **idempotency key** identifies one logical client operation across network
retries. Payment clients commonly retry after timeouts where the server may
have completed the request but the response was lost. Persisting the key and
the result prevents a retry from initiating a second payment. The key is
unique per transfer; `reference` is independently unique as a business-level
duplicate guard.

**2PC vs. saga:** two-phase commit coordinates prepare/commit across
transactional resources and can provide atomicity, but requires resource
managers to participate and can hold locks while the coordinator is
unavailable. A saga commits local transactions in sequence and uses
compensating actions when later work fails; it is more suitable for
long-running workflows across services/brokers, but compensation is
eventual and business-specific rather than a true rollback. Use 2PC when all
participants support it and strict atomic commit is required; use a saga when
the workflow spans independently owned services or non-transactional
resources.

## Tests

Run the unit tests with:

```bash
./mvnw test
```
