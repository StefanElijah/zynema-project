# ADR-0026: The outbox is one shared component, not per-service code

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 7 (async messaging)

## Context

ADR-0008 decided the pattern: append the event in the business transaction,
publish it afterwards with a relay, accept at-least-once. What it did not
decide is where that code lives and how the consumer side answers "have I
already handled this?", and two producers (payment now, playback next) plus
three consumers would each have invented their own.

## Decision

**One implementation in `zynema-common`, opt-in per service by adding the
table.** The component is JDBC-based on purpose — no JPA entity, no
`@EntityScan` in a library, no persistence-context surprises:

- `OutboxRecorder.append(topic, subject, payload)` serialises the payload to
  JSON, derives the type through `EventTypes`, takes the correlation id from
  the MDC and inserts the row. Called from inside the service's existing
  `@Transactional` method, which is the whole guarantee: the row and the state
  change commit or roll back together.
- `OutboxRelay` polls unpublished rows in insertion order and publishes with
  the **stored** `eventId`/`type`/`occurredAt`. That matters: regenerating the
  id on a republish would defeat every consumer's deduplication.
- Publishing stops at the first failure, so a half-available broker does not
  reorder a batch; the row stays unpublished and the next tick retries.
- The initial delay is a property (`zynema.messaging.outbox.initial-delay`)
  beyond the poll interval precisely so tests can park the scheduler and drive
  the relay by hand.
- Consumers deduplicate with `ProcessedEventStore.claim(eventId, handler)`: one
  `INSERT … ON CONFLICT DO NOTHING` in the same transaction as the handler's
  work. A rolled-back handler leaves no claim, so the redelivery is processed.

Both tables (`outbox`, `processed_events`) belong to the **service that uses
them**, not to the library: each service adds its own migration, so the schema
is owned where it is written and the library stays dependency-light.

## Consequences

**Positive**

- One behaviour to reason about and one place to fix: the relay's ordering,
  the stored-id rule and the claim are identical in every service.
- A service that does not want messaging simply does not create the table; the
  component is inactive without a `JdbcTemplate`.
- The tests that matter are integration tests: `OutboxTests` proves the commit
  path, the rollback path (no row, nothing published) and the republish path
  (same `eventId` twice) against a real database and a real broker.

**Negative**

- Two mechanisms to operate per producer (table growth, relay latency). The
  outbox table has no cleanup job yet; rows stay published-but-present, which
  is acceptable for now and would be a partition/TTL job in production.
- At-least-once is real: a crash between publishing and marking republishes.
  Consumers that forget `ProcessedEventStore` will process twice — the test
  suite can only prove the store works, not that every future consumer uses it.
- The relay assumes a single instance per service; two would publish
  duplicates (still at-least-once, but noisier). `FOR UPDATE SKIP LOCKED` is
  the documented escalation.

## Notes

- The outbox stores the payload as text, not `jsonb`: it is written and read by
  the application, never queried by SQL. Keeping it opaque avoids a cast in
  every direction and a schema the database could reject mid-transaction.
- Playback-service will adopt the same component when its session events become
  event-sourced (ADR-0009, ADR-0027): the session's event log is the aggregate,
  the outbox is the same rows leaving the service.
