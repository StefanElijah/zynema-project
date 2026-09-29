-- ══════════════════════════════════════════════════════════════════════
-- V2__consumers.sql — Fase 7 (ADR-0025, ADR-0026, ADR-0007).
--
-- Design notes:
--  * `contacts` is the projection built from UserEvent.UserRegistered: the
--    email is published once, in that event, and never travels in commands.
--  * `notification_log` is the delivery record, keyed by the notification id
--    the service minted; it is what the saga and support read.
--  * `notification_dead_letters` persists what the retry topics could not
--    process, so a poison pill is visible instead of only being on a topic.
--  * `processed_events` gives every consumer its idempotency claim, and
--    `outbox` lets NotificationSent/Failed leave atomically with the log
--    (ADR-0026). Each table belongs to this service.
-- ══════════════════════════════════════════════════════════════════════

CREATE TABLE contacts (
    user_id      uuid         PRIMARY KEY,
    email        varchar(320) NOT NULL,
    display_name varchar(255),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE contacts IS
    'Contact projection fed by UserEvent.UserRegistered: the only place the email is published.';

CREATE TABLE notification_log (
    notification_id uuid         PRIMARY KEY,
    user_id         uuid         NOT NULL,
    template        varchar(80)  NOT NULL,
    -- Nullable on purpose: a failure recorded from the dead-letter path may
    -- not have a contact to name.
    recipient       varchar(320),
    status          varchar(20)  NOT NULL,
    reason          varchar(500),
    source_event_id uuid,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_status CHECK (status IN ('SENT', 'FAILED'))
);

CREATE INDEX idx_notification_log_user ON notification_log (user_id, created_at DESC);

COMMENT ON TABLE notification_log IS
    'Delivery history: one row per notification the service ever tried to send.';

CREATE TABLE notification_dead_letters (
    id          uuid         PRIMARY KEY,
    topic       varchar(160) NOT NULL,
    partition   integer      NOT NULL,
    record_offset bigint     NOT NULL,
    subject     varchar(255),
    event_type  varchar(120),
    event_id    uuid,
    payload     text         NOT NULL,
    error       varchar(2000),
    failed_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_dead_letter_record UNIQUE (topic, partition, record_offset)
);

COMMENT ON TABLE notification_dead_letters IS
    'Retries exhausted: the record as it arrived, so nothing is lost on a topic nobody reads.';

CREATE TABLE processed_events (
    event_id     uuid         NOT NULL,
    handler      varchar(120) NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, handler)
);

CREATE TABLE outbox (
    id           uuid         PRIMARY KEY,
    topic        varchar(120) NOT NULL,
    subject      varchar(255) NOT NULL,
    type         varchar(120) NOT NULL,
    payload      text         NOT NULL,
    correlation_id varchar(120),
    occurred_at  timestamptz  NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    published_at timestamptz
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

COMMENT ON TABLE outbox IS
    'Transactional outbox: append in the business transaction, publish via OutboxRelay.';
