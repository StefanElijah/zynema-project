-- ══════════════════════════════════════════════════════════════════════
-- V2__event_sourcing.sql — the session aggregate becomes an event stream
-- (ADR-0009, ADR-0027).
--
-- Design notes:
--  * `session_events` is append-only: a session's state is the fold of its
--    events, and no UPDATE or DELETE ever touches this table.
--  * PARTITION BY HASH (session_id): a stream is always read as a whole, so
--    all of a session's events live in the same partition, and the unique
--    (session_id, sequence) stays enforceable because it includes the
--    partition key.
--  * `sequence` is per session and dense. (session_id, sequence) is the
--    optimistic concurrency check: a racing append loses on the unique index
--    instead of silently interleaving two writers.
--  * `playback_sessions` (V1) remains as the projected read model: the
--    cross-session queries (open sessions, concurrency limit, stream
--    ownership) are the ones a fold per request cannot serve. Same shape as
--    catalog's content_read_model (ADR-0022), written in the same
--    transaction as the events.
--  * `session_snapshots` keeps the fold short: every N events the current
--    state is stored as JSON and only the tail after it is replayed.
-- ══════════════════════════════════════════════════════════════════════

CREATE TABLE session_events (
    event_id    UUID         NOT NULL,
    session_id  UUID         NOT NULL,
    sequence    BIGINT       NOT NULL,
    type        VARCHAR(120) NOT NULL,
    payload     TEXT         NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (session_id, event_id),
    CONSTRAINT uq_session_event_sequence UNIQUE (session_id, sequence)
) PARTITION BY HASH (session_id);

CREATE TABLE session_events_p0 PARTITION OF session_events FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE session_events_p1 PARTITION OF session_events FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE session_events_p2 PARTITION OF session_events FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE session_events_p3 PARTITION OF session_events FOR VALUES WITH (MODULUS 4, REMAINDER 3);

COMMENT ON TABLE session_events IS
    'Append-only event log of the session aggregate: state is the fold of these rows (ADR-0027).';

CREATE TABLE session_snapshots (
    session_id UUID        PRIMARY KEY,
    sequence   BIGINT      NOT NULL,
    state      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE session_snapshots IS
    'Fold checkpoints: the state after `sequence`, overwritten as the session advances.';

COMMENT ON COLUMN session_snapshots.sequence IS
    'The stream sequence this state includes; events after it are replayed on load.';
