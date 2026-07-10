# ADR-0006: CQRS en catalog-service

- **Status:** Accepted
- **Date:** 2026-07-09
- **Deciders:** Project owner

## Context

The catalog is read a lot (every page of the SPA reads it) and written
rarely (an operator adds a movie a few times per day). Naively, we use
the same JPA model for both. That works, but it makes us pay the cost of
JPA's change tracking and entity hydration on the hot read path.

## Decision

We adopt a **lightweight CQRS** in `catalog-service`:

- **Write side**: a normal JPA-backed `Movie` / `Series` entity, with
  validation, business rules, and Flyway-migrated tables.
- **Read side**: a **projection** maintained in the same DB but in a
  separate schema or table (`catalog_read_model`). The BFF reads from
  the projection, not the write model.
- The projection is updated in the same transaction as the write
  (using Spring's `TransactionSynchronization.afterCommit`), or via an
  outbox + event consumer if we want true decoupling later.

## Rationale

- Reads become simple `SELECT` against a denormalized table — fast and
  cache-friendly.
- Writes remain expressive and use the full JPA features.
- We don't need a separate read database (no Elasticsearch yet) — the
  same PostgreSQL serves both, which keeps the local dev story simple.
- This is **not** full Event Sourcing. The write side is the source of
  truth.

## Consequences

- `catalog-service` exposes two repository interfaces:
  `CatalogWriteRepository` (JPA) and `CatalogReadRepository` (JdbcTemplate
  or jOOQ against the projection).
- The write service must call the projection updater after every
  successful write.
- The BFF reads from `CatalogReadRepository` only.
- If the write succeeds and the projection update fails, the read model
  is stale. We accept eventual consistency (max seconds).

## Trade-offs accepted

- Two models to maintain. Both small in this case.
- Slight eventual consistency on reads (catalog shows new content
  within seconds, not instantly).
- Operational: a read model migration when the write model changes.

## When to escalate

If read requirements outgrow a single PostgreSQL (full-text search,
recommendations, faceting), we move the read model to OpenSearch or
similar. The CQRS seam makes that swap easier.
