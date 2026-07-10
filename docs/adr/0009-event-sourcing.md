# ADR-0009: Event Sourcing en playback-service

- **Status:** Accepted (experimental)
- **Date:** 2026-07-09
- **Deciders:** Project owner

## Context

Playback sessions are inherently **append-only**: a user starts, pauses,
seeks, resumes, and stops. The full history is interesting:

- For analytics ("how often do users abandon in the first 5 minutes?").
- For audit ("did user X really watch this content?").
- For debugging ("why did the player crash on minute 32?").

A traditional JPA model stores the **current state** of a session. We
lose the history unless we add an audit table (which is just event
sourcing by another name).

## Decision

We use **Event Sourcing** in `playback-service` for the **session
aggregate**:

- The session's state is the **fold** of all events for that session.
- Events are stored in an `append_only_events` table, partitioned by
  session id.
- Snapshots are taken every N events to keep the fold fast.
- The current state is materialized in memory and persisted as a
  snapshot.

## Rationale

- The history **is** the data. Storing it as events is the most direct
  model.
- We can build new read models by replaying events.
- It pairs naturally with the Outbox pattern: session events are
  written in the same transaction as the outbox row, and relayed to
  Kafka for downstream consumers.

## Consequences

- `playback-service` is the only one using event sourcing. Other
  services use traditional state.
- The session aggregate has **no UPDATE** in SQL — only inserts.
- A new developer on the project has a learning curve. We add docs
  in `docs/learning/event-sourcing.md`.
- The data model is more complex than a single sessions table.

## Trade-offs accepted

- We are explicitly using ES for **one** service, as a learning
  exercise. This is a conscious scope choice: see the project
  README, "Aprendizaje profundo" decision.
- Operational: the events table grows. We add a partitioning strategy
  and a TTL on snapshots older than X days.

## When **not** to use

- If the data is purely current state with no audit value (e.g., a
  user's password hash), use a normal table.
- If reads dominate and the history is irrelevant, use a normal table.
