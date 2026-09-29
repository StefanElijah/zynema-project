-- Fase 7 (ADR-0007, ADR-0029): the orchestrated onboarding saga.
--
-- Payment drives the flow explicitly: it starts a saga when its own
-- SubscriptionCreated is published, sends a GrantRole command, waits for the
-- reply, sends SendNotification, waits, and compensates if the role step
-- fails. The state of every flow is in one table, which is the whole point of
-- orchestration: "where is subscription X?" is a query, not a Kafka dig.

CREATE TABLE subscription_onboarding_sagas (
    id              uuid         PRIMARY KEY,
    subscription_id uuid         NOT NULL UNIQUE,
    user_id         uuid         NOT NULL,
    plan_code       varchar(40),
    state           varchar(30)  NOT NULL,
    notification_id uuid,
    failure_reason  varchar(500),
    started_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_onboarding_saga_state CHECK (
        state IN ('AWAITING_ROLE', 'AWAITING_NOTIFICATION', 'COMPLETED', 'COMPENSATED')
    )
);

CREATE INDEX idx_onboarding_sagas_user ON subscription_onboarding_sagas (user_id, state);

-- The reply from notification is matched by the id the saga minted.
CREATE INDEX idx_onboarding_sagas_notification ON subscription_onboarding_sagas (notification_id);

COMMENT ON TABLE subscription_onboarding_sagas IS
    'Explicit state of the orchestrated onboarding saga (ADR-0029).';
