# ADR-0003: Database per service desde el día 1

- **Status:** Accepted
- **Date:** 2026-07-09
- **Deciders:** Project owner

## Context

In a microservice system, the unit of ownership is the **service**, not
the database. A common anti-pattern is to start with a single shared
PostgreSQL instance and multiple schemas, then run into:

- Cross-service joins that hide tight coupling.
- One team breaking another's table in migrations.
- A single point of failure for the whole platform.
- No independent scaling of read-heavy services.

## Decision

We adopt **database per service from day 1**. Each microservice owns one
PostgreSQL database (`zynema_catalog`, `zynema_user`, `zynema_payment`,
etc.) and no other service can read or write to it.

Cross-service data is exchanged via:

- **APIs** (OpenFeign clients) for synchronous reads.
- **Events** (Kafka) for asynchronous state propagation.

## Rationale

- Strong bounded contexts.
- Each service can scale, migrate, or rewrite its DB independently.
- Refusing shared DBs forces us to think about the **contract** between
  services, not the **storage** they share.

## Consequences

- We provision 6 PostgreSQL databases on first boot
  (`infra/scripts/postgres-init/01-create-databases.sh`).
- We accept that **there is no global transaction** across services.
  We compensate with **Saga** (ADR-0007) and **Outbox** (ADR-0008).
- We need a `dev` workflow that lets a single dev bring up all 6 DBs at
  once: that's what the `core` profile does.
- Local resource cost is higher (6 DBs vs 1), but on 16GB RAM it's fine
  since they're all in the same PostgreSQL container.
