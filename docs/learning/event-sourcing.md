# Event Sourcing — practical notes

This is how event sourcing actually looks in `playback-service`
(ADR-0009, ADR-0027, and the outbox in ADR-0026). It is a learning project's
implementation, so the notes are about concrete trade-offs, not the ideal
pattern.

## The idea in one sentence

A session is not a row; it is **the ordered list of things that happened to
it**. The current state is what you get by folding that list.

```
SessionStarted(pos 0) → SessionProgressed(300) → SessionProgressed(900) → SessionStopped(1200)
        │                        │                        │                       │
        └────────────────────────┴───────────┬────────────┴───────────────────────┘
                                             ▼
                                  SessionState(position 1200, watched 1200, ENDED)
```

`SessionState.apply(state, event)` is the entire definition of what a session
means. It is a pure function over records, tested without Spring or a database
(`SessionStateTests`).

## The four pieces

| Piece      | Table / class                             | Role                                                                                                                      |
| ---------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Event log  | `session_events`                          | Source of truth. Append-only, one row per fact, dense sequence per session.                                               |
| Snapshot   | `session_snapshots`                       | A checkpoint of the fold (`state` JSON after `sequence`) so loads replay a short tail. Optimisation, never truth.         |
| Projection | `playback_sessions` + `SessionProjection` | Read model for cross-session queries (open session, concurrency, ownership). Derived, atomic with the event, rebuildable. |
| Outbox     | `outbox` + shared `OutboxRecorder`        | The same event leaves to Kafka, with the **same event id**, inside the same transaction.                                  |

A command is always the same loop, in one transaction:

```
load(stream)  →  decide(new event)  →  append(event)  →  project(new state)
```

- `SessionEventStore.load` reads the latest snapshot, then the events after it,
  and folds them.
- `SessionEventStore.append` inserts the event at `lastSequence + 1`, records
  the outbox row with the same id, and writes a snapshot every N events.
- `SessionProjection.update` upserts the one row the queries need.

## Why the projection still exists

Event sourcing does not replace queries; it changes where the truth lives. If
every read folded the whole log, "which sessions are open for this account"
would read the entire history of the account. So:

- The **write side** goes through the log (the aggregate).
- The **read side** for cross-session questions goes through the projection.
- The projection is written **in the same transaction** as the event: a
  rollback cannot leave a session that never happened. This is the same rule
  as catalog's CQRS read model (ADR-0022).

This is CQRS with event sourcing behind the write model, not instead of it.

## Invariants worth testing

- The first event of a stream is `SessionStarted`; a second one is a bug.
- Nothing follows `SessionStopped`; a closed stream is immutable.
- An event from another session never merges into a stream.
- The fold of the whole log equals the projection row (the tests assert it).
- A rolled-back append leaves neither an event nor an outbox row.
- Two writers from the same sequence: one wins, the loser gets a 409 — never
  an interleaved stream.

## Operational notes

- **Snapshots** are written every `zynema.playback.events.snapshot-every`
  events (default 20). Losing a snapshot only costs replay time.
- **Rebuild**: with the projection lost, replay every session's stream and
  re-project. The log is enough; the tooling is not written yet.
- **Cleanup**: covered events _could_ be archived and snapshots of closed
  sessions deleted; neither job exists yet, and the hash partitioning by
  `session_id` is the groundwork for it.
- **Concurrency**: the unique `(session_id, sequence)` is the optimistic lock.
  PostgreSQL aborts the transaction on the violation, so the retry is the
  caller's job — the 409 tells it to reload.

## When not to use it

- Current state with no history value (a password hash) is a row, not a
  stream.
- If reads dominate and history is irrelevant, a normal table plus a cache is
  simpler and faster.
- Event sourcing is a data-model decision, not a messaging one: the topic and
  the log happen to share payloads here, but you can have one without the
  other. We kept them identical on purpose (ADR-0027) to have a single
  contract to maintain.
