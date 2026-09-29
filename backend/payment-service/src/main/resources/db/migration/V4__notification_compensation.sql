-- Fase 7 (ADR-0007, ADR-0025): the compensation side of the choreographed
-- saga.
--
-- When notification-service gives up on the welcome email it publishes
-- NotificationFailed; payment records that the subscription's notification
-- never landed and exposes it. Money is NOT reversed: the subscription keeps
-- its status and the failure is a flagged fact, not an undo.

CREATE TABLE processed_events (
    event_id     uuid         NOT NULL,
    handler      varchar(120) NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, handler)
);

COMMENT ON TABLE processed_events IS
    'Consumer idempotency: one row per (event, handler), claimed in the handler transaction (ADR-0026).';

CREATE TABLE subscription_notification_failures (
    notification_id uuid        PRIMARY KEY,
    subscription_id uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    template        varchar(80) NOT NULL,
    reason          varchar(500),
    occurred_at     timestamptz NOT NULL,
    received_at     timestamptz NOT NULL DEFAULT now()
);

-- "The latest failure of this subscription" is the query the read side makes.
CREATE INDEX idx_notification_failures_subscription
    ON subscription_notification_failures (subscription_id, occurred_at DESC);

COMMENT ON TABLE subscription_notification_failures IS
    'Choreographed compensation: email deliveries that failed permanently, kept as billing history.';
