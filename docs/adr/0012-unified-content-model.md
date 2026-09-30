# ADR-0012: One `content` table with a type discriminator (not separate `movies` / `series`)

- **Status:** Accepted
- **Date:** 2026-09-23
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 2 (persistence and first domain)

## Context

The catalog has to store movies and series. The obvious relational shape is two
tables (`movies`, `series`) with their own columns, plus `seasons` and
`episodes` hanging off `series`.

That shape breaks down as soon as the product does what streaming products do:

- **One search box.** Users search "dune" and expect the movie _and_ the series
  in one ranked list. Two tables mean either two queries plus in-memory merge
  (impossible to paginate correctly) or a `UNION` that cannot use one index.
- **One popularity ranking.** Rails ("Trending now", "Top 10") mix movies and
  series. With two tables, every rail is a merge of two ordered sets.
- **One metadata model.** `metadata` (franchise, awards, box office, country)
  applies identically to both.
- **New content types.** Documentaries, live events, or shorts would each need
  another table and another merge.

## Decision

We store movies and series in a single `content` table with a `type`
discriminator (`MOVIE` | `SERIES`). Series-only data lives in child tables
(`seasons`, `episodes`); everything shared lives in typed columns; everything
flexible lives in a JSONB `metadata` column.

Endpoints stay explicit: `/api/v1/catalog/movies/**` and
`/api/v1/catalog/series/**` are thin filters over the same query path, so the
API surface does not leak the storage decision.

## Consequences

**Positive**

- One full-text index over `title` + `original_title` serves every search.
- One `popularity` / `average_rating` ordering serves every rail.
- Adding a content type is a `CHECK` constraint change, not a schema migration
  of a new table plus new endpoints plus new merge logic.
- `metadata` absorbs attributes that differ per title without schema churn.

**Negative**

- Columns that only apply to one type are nullable (`runtime_minutes` is null
  for series; `seasons` is empty for movies). Enforced by validation and by
  service rules, not by the schema.
- A `CHECK (type IN (...))` constraint must be updated deliberately when a new
  type is added — which is the point: it is an explicit decision.

## Notes

- `runtime_minutes` on `content` describes a movie's runtime; an episode's
  runtime lives on `episodes.runtime_minutes`.
- `status` (`DRAFT` | `PUBLISHED` | `ARCHIVED`) exists from day one so the
  public read API can filter to `PUBLISHED` while admins still see drafts.
