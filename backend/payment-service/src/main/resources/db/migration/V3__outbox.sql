-- Fase 7 (ADR-0008, ADR-0026): the outbox.
--
-- Events are appended inside the business transaction and published by the
-- relay afterwards. At-least-once by design: a crash between publishing and
-- marking republishes, and consumers deduplicate by event id.

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

-- The relay only ever asks for unpublished rows, in insertion order.
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

COMMENT ON TABLE outbox IS
    'Transactional outbox: append in the business transaction, publish via OutboxRelay.';
