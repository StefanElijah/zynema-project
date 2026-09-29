# ADR-0027: Event sourcing for the session aggregate

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 7 (async messaging), commit 2b

## Context

ADR-0009 chose event sourcing for `playback-service` back in Phase 0, when the
service was a plan and a JPA entity. Fase 7 is where the two forces meet: the
session events are also the platform's first playback producer, and the outbox
(ADR-0026) promised that "the session's event log is the aggregate, the outbox
is the same rows leaving the service".

Between ADR-0009 and the code there were still open questions: what exactly
goes in the first event, whether the existing `playback_sessions` table
disappears, and how a client finds an open session if every read is a fold.

## Decision

**The log is the source of truth and the table people query is a projection.**

- `session_events` is append-only: `(event_id, session_id, sequence, type,
payload, occurred_at)`, primary key `(session_id, event_id)`, unique
  `(session_id, sequence)`, and **nothing** issues an UPDATE or DELETE against
  it. It is hash-partitioned by `session_id` (four partitions to start): a
  stream is always read as a whole, so one session's events stay in one
  partition, and the unique constraint stays enforceable because it includes
  the partition key. ADR-0009 called it `append_only_events`; the implemented
  name says whose events they are.
- **Events are the public `PlaybackEvent` payloads**, the same records that go
  to Kafka. `SessionStarted` gained the full start state (`profileId`,
  `episodeId`, `contentTitle`, `device`, `durationSeconds`) precisely so the
  fold can build a session from nothing. There is no internal event dialect to
  keep in sync with the external one.
- **`playback_sessions` stays, as a synchronous projection.** The queries that
  matter — an open session to resume, the concurrency count, ownership of a
  stream — go across sessions; folding every stream per request is the wrong
  shape. It is written by `SessionProjection` (JDBC upsert) **inside the same
  transaction as the event**, the same rule as catalog's read model
  (ADR-0022): derived data is atomic with its source, and rebuildable from it.
- **Snapshots** (`session_snapshots`) keep the fold short: every N events
  (`zynema.playback.events.snapshot-every`, default 20) the folded state is
  stored as JSON and a load replays snapshot plus tail. The snapshot is an
  optimisation, never a source of truth.
- **The outbox row is the same fact**: `OutboxRecorder.append(topic, subject,
eventId, occurredAt, payload)` records the log entry's identity, so the row
  in `session_events` and the Kafka message are matchable and a republish
  cannot mint a new id.
- **Concurrency is optimistic**: appending at a stale sequence is a 409, not an
  interleaved stream. Retrying inside the aborted PostgreSQL transaction is
  impossible, so the retry belongs to the caller. The start race is resolved
  without exceptions: the projection insert is `ON CONFLICT DO NOTHING`
  against the partial unique index, so the loser rolls back cleanly and reads
  the winner. `SessionStopped` is final: a closed stream accepts no commands.
- `watchedSeconds` is an approximation (the sum of forward position deltas)
  and is documented as analytics, not billing.

## Consequences

**Positive**

- The history is the data: abandonment, audits and "why did the player stop at
  minute 32" are queries over rows that already exist, and the same rows leave
  the service.
- The projection can be rebuilt from the log; a new read model needs a fold,
  not a schema migration.
- The write path is uniform: every command is load → append → project in one
  transaction, and the tests can prove the fold and the projection agree.
- `SessionState.apply` is the single definition of the lifecycle; invariants
  (one start, nothing after stop, no cross-session events) fail while the
  transaction can still roll back.

**Negative**

- Each event is two writes (log + projection) plus the outbox row. The
  projection duplicates state that the log could recompute.
- Tests have to snapshot aggressively (every 2 events in the suites) to
  exercise both the snapshot path and the tail; production snapshots every 20
  and still needs a cleanup job for snapshots of long-closed sessions — not
  written yet, the same pending partition/TTL job ADR-0009 accepted.
- A full projection rebuild is a replay tool that does not exist yet; losing
  `playback_sessions` today means re-projecting by hand.
- The public start event grew fields that only the local fold consumes
  (`device`, `contentTitle`). The alternative — a private event dialect — was
  rejected because "the outbox is the same rows" would stop being true.

## Notes

- Snapshots are stored as text, not `jsonb`: they are written and read by the
  application, never queried by SQL. Same reasoning as the outbox payload.
- `SessionEventStore.load` is the only reader of the log; `SessionProjection`
  is the only writer of the projection. One direction each, no loops.
- The end-to-end tests use a real broker and a real Schema Registry: the
  assertion that matters is that the event id in `session_events` equals the
  `eventId` header of the message on `zynema.playback.events`.
