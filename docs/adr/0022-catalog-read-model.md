# ADR-0022: A projected read model for the catalogue

- **Status:** Accepted
- **Date:** 2026-09-28
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 5 (BFF reactive and CQRS)

## Context

ADR-0006 decided that catalog-service would be CQRS: the write side stays a JPA
aggregate with its invariants, the read side answers from a projection. Fase 2
built the write side and a read service that was "CQRS-ready" — in practice it
read the _same_ tables through JPA, hydrating entities, batch-loading genres and
assembling DTOs in Java. Every list request was a specification query plus a
second query for genres, and every detail request fanned out to credits and
seasons.

With the BFF now composing screens that fan out to the catalogue, the read path
is the hot path. The question is what the projection should be.

## Decision

A **denormalised table with typed JSONB payloads**, maintained by a projector in
the writer's transaction:

- `content_read_model` holds one row per title: columns for what SQL must
  filter, sort and search (`type`, `status`, `release_year`, `average_rating`,
  `popularity`, `genre_slugs` as a JSON array with a GIN index, and a
  `search_vector` for full-text search over the title) and three JSONB payloads
  ready to serve: `summary` (a `ContentSummaryDto`), `detail` (a
  `ContentDetailDto`) and `episodes` keyed by season number.
- **The projector runs inside the write transaction** (`CatalogCommandService`
  → `CatalogProjector`), after the entity is flushed. The projection is then
  atomic with the write: no after-commit window, no lost projection on a crash,
  and a client can read its own write. ADR-0006 left the door open to an outbox
  - consumer; that is Fase 7 work and the wrong tool for a same-database
    projection.
- **Reads only see the projection.** `CatalogQueryService` has no JPA
  repository left for content: lists, search, detail and episodes are SQL over
  the read model, and the payloads are deserialised into the same DTOs, so the
  HTTP contract does not move. Genres are the deliberate exception — reference
  data, not projected state.
- **The model can be rebuilt**: an empty projection is backfilled from the write
  tables at startup, and a full rebuild is one call (`CatalogProjector.rebuildAll()`)
  because projection only reads the source of truth and writes the same rows
  again.

Why a table and not a PostgreSQL materialised view: the payloads are typed DTOs
that must survive DTO evolution under test (assembled in Java, where the mappers
already live), the projection must be transactional with the write, and a
`REFRESH MATERIALIZED VIEW` cannot run inside that transaction — its
`CONCURRENTLY` variant cannot at all. Why not jOOQ: no other service uses it,
and `NamedParameterJdbcTemplate` with a whitelist of sort columns covers this
query set without new tooling.

## Consequences

**Positive**

- One indexed lookup per screen: a list is one SQL statement, a detail is one
  row, and the N+1 assembly in Java is gone.
- The write model can change (new columns, new invariants) without touching the
  read contract; a projection bug is a rebuild, not a data loss.
- The search vector and the genre index are maintained where the data changes,
  not recomputed per query.

**Negative**

- Writes got heavier: every mutation now reads seasons/credits and upserts one
  JSONB row. Catalogue writes are rare and admin-only, which is the trade CQRS
  makes; a bulk import would need a batched projector.
- The projection can be wrong (a missed `project` call) in a way a materialised
  view could not. Mitigations: the command service is the only writer, the
  projector is called in the same code path, and the count parity between both
  models is asserted by a test.
- Two copies of the same data exist. A future heavy read (facets, fuzzy search)
  moves to OpenSearch, as ADR-0006 anticipated — the seam is already here.

## Notes

- The projector reads through JDBC, so the command service must **flush before
  projecting** (`saveAndFlush`): an un-flushed `save()` would project the
  previous version. This is the one trap that bit during implementation.
- `episodes` is a map because a missing key means "no such season" (404) while
  an empty list means "season with no episodes" (200) — a distinction the old
  per-season table lookup made naturally.
- Sort parameters are never interpolated: they resolve against a map of allowed
  column names, so ordering cannot be used to inject SQL.
