# ADR-0008: Outbox Pattern para garantía transaccional de eventos

- **Status:** Accepted
- **Date:** 2026-07-09
- **Deciders:** Project owner

## Context

When a service wants to emit an event, the naive approach is:

1. Commit the business transaction to the DB.
2. Send the event to Kafka.

If the service crashes between step 1 and step 2, the event is **lost**.
If the order is reversed, the event may be sent for a transaction that
later rolls back — a **phantom event**.

This is the classic **dual write problem**.

## Decision

We use the **Outbox Pattern**:

- In the same DB transaction as the business write, we **insert a row
  into an `outbox` table** with the event payload.
- A separate **relay process** (a scheduled job, or a Debezium-like CDC,
  or a Spring `ApplicationListener` that reads unpublished rows)
  publishes the outbox rows to Kafka and marks them as published.
- The relay is **at-least-once**. Consumers must be idempotent.

## Rationale

- The outbox is just a table. We can replicate it, back it up, replay it.
- The dual-write race condition is structurally eliminated.
- We don't need a heavy CDC infrastructure (Debezium) in dev — a simple
  scheduled reader works.

## Consequences

- `payment-service` and other event-producing services have an
  `outbox` table, an `OutboxEntry` entity, and an `OutboxRelay`
  scheduled task.
- Consumers **must** be idempotent (use the event id as a dedup key in
  Redis or a unique constraint on a `processed_events` table).
- Slight latency added to event delivery (poll interval vs. instant
  publish). Acceptable.

## Trade-offs accepted

- A second mechanism (the relay) must be operated. It's tiny, but it's
  a thing.
- We accept that events can be **re-delivered** (at-least-once). We
  don't promise exactly-once.

## When to escalate

If event volume grows, we can move the relay to a Debezium + Kafka
Connect setup, or switch to PostgreSQL `LISTEN/NOTIFY`-based triggers.
The outbox table stays the same; only the relay implementation changes.
