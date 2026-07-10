# ADR-0004: API versionada desde el día 1

- **Status:** Accepted
- **Date:** 2026-07-09
- **Deciders:** Project owner

## Context

There are two schools of thought on API versioning:

1. **No versioning**: change endpoints in place, accept breaking changes
   with migration periods.
2. **Versioned from day 1**: every public endpoint lives under
   `/api/v1/...`, breaking changes go to `/api/v2/...`.

## Decision

We adopt **option 2**. Every public endpoint is under `/api/v1/...`.

The frontend (BFF) talks to the BFF; the BFF talks to internal services
over versioned URLs. Even internal OpenFeign clients use `/api/v1/...`
explicitly in the URL.

## Rationale

- The cost of versioning from day 1 is near zero (a path prefix).
- The cost of retrofitting it later is enormous.
- The BFF is the only thing the SPA depends on; we can evolve internal
  APIs without breaking it as long as we increment the version.

## Consequences

- All controllers in domain services are `@RequestMapping("/api/v1/...")`.
- The BFF translates its own public URLs to internal versioned URLs.
- The first breaking change forces a v2; the v1 keeps working until
  explicitly retired.
- We document versions in springdoc-openapi specs.

## Trade-offs accepted

- Some URL noise (`/api/v1/catalog/movies` instead of `/catalog/movies`).
  Acceptable.
- We must keep v1 alive for deprecation windows. Acceptable.
