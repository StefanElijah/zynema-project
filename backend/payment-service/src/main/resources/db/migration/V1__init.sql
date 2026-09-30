-- ══════════════════════════════════════════════════════════════════════
-- V1__init.sql — payment-service schema baseline
--
-- Design notes:
--  * `user_id` is the LOCAL account id owned by user-service, resolved over
--    Feign on the first authenticated call (ADR-0019). The IdP subject never
--    enters this schema, so swapping identity providers does not touch
--    billing data.
--  * One ACTIVE subscription per account, enforced by a partial unique index
--    rather than by application code.
--  * `idempotency_keys` stores the full response so a retried request replays
--    the original answer instead of charging twice (ADR-0017). The row is
--    reserved before the business logic runs, so concurrent duplicates cannot
--    both execute.
--  * Amounts live in NUMERIC, never in floating point.
-- ══════════════════════════════════════════════════════════════════════

CREATE TABLE plans (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(40)   NOT NULL UNIQUE,
    name           VARCHAR(120)  NOT NULL,
    description    TEXT,
    price          NUMERIC(10, 2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL DEFAULT 'USD',
    billing_period VARCHAR(20)   NOT NULL,
    max_streams    INTEGER       NOT NULL DEFAULT 1,
    max_quality    VARCHAR(10)   NOT NULL DEFAULT 'SD',
    features       JSONB         NOT NULL DEFAULT '{}'::jsonb,
    active         BOOLEAN       NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_plan_price CHECK (price >= 0),
    CONSTRAINT chk_plan_period CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT chk_plan_streams CHECK (max_streams > 0),
    CONSTRAINT chk_plan_quality CHECK (max_quality IN ('SD', 'HD', 'FHD', 'UHD'))
);

CREATE INDEX idx_plans_active ON plans (active, price);

CREATE TABLE subscriptions (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL,
    plan_id              UUID        NOT NULL REFERENCES plans (id),
    status               VARCHAR(20) NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end   TIMESTAMPTZ NOT NULL,
    cancel_at_period_end BOOLEAN     NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    canceled_at          TIMESTAMPTZ,
    CONSTRAINT chk_subscription_status CHECK (status IN ('ACTIVE', 'CANCELED', 'PAST_DUE')),
    CONSTRAINT chk_subscription_period CHECK (current_period_end > current_period_start)
);

CREATE INDEX idx_subscriptions_user ON subscriptions (user_id, created_at DESC);

-- At most one active subscription per account.
CREATE UNIQUE INDEX uq_subscription_active ON subscriptions (user_id) WHERE status = 'ACTIVE';

CREATE TABLE payments (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id    UUID           NOT NULL REFERENCES subscriptions (id) ON DELETE CASCADE,
    amount             NUMERIC(10, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    method             VARCHAR(40)    NOT NULL,
    provider_reference VARCHAR(80),
    paid_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_amount CHECK (amount >= 0),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED'))
);

CREATE INDEX idx_payments_subscription ON payments (subscription_id, created_at DESC);

CREATE TABLE idempotency_keys (
    key           VARCHAR(255) PRIMARY KEY,
    request_hash  VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    response_code INTEGER,
    response_body JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);
