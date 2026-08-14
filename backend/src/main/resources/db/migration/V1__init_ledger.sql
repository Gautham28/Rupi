CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_user_username UNIQUE (username)
);

CREATE TABLE account (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user (id),
    balance NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_account_user UNIQUE (user_id),
    CONSTRAINT ck_account_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE transfer (
    id UUID PRIMARY KEY,
    from_account_id UUID NOT NULL REFERENCES account (id),
    to_account_id UUID NOT NULL REFERENCES account (id),
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfer_not_self CHECK (from_account_id <> to_account_id)
);

CREATE INDEX idx_transfer_from_created_id
    ON transfer (from_account_id, created_at DESC, id DESC);

CREATE INDEX idx_transfer_to_created_id
    ON transfer (to_account_id, created_at DESC, id DESC);

CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user (id),
    idempotency_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    http_status INTEGER,
    response_body JSONB,
    transfer_id UUID REFERENCES transfer (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);

CREATE TABLE demo_credit_event (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account (id),
    amount NUMERIC(19, 2) NOT NULL,
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_demo_credit_positive CHECK (amount > 0),
    CONSTRAINT uq_demo_credit_account_key UNIQUE (account_id, idempotency_key)
);
