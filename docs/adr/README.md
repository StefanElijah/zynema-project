# Architecture Decision Records (ADRs)

This folder contains the ADRs for the Zynema platform. Each ADR records
**one** significant architectural decision: context, options considered,
the choice made, and its consequences.

## Conventions

- Filename: `NNNN-short-slug.md` (zero-padded, monotonic, never re-used).
- Status: `Accepted` once merged into `main` / `develop`. `Proposed` while
  still under discussion. `Superseded` if replaced (link to the new ADR).
- Tone: present tense, first-person plural ("we will…"). Be opinionated.
- Length: aim for 1-2 pages. If it grows past 3, split it.

## Index

| #    | Title                                                                        | Status   |
| ---- | ---------------------------------------------------------------------------- | -------- |
| 0001 | [Monorepo multi-stack with Nx](0001-monorepo-nx.md)                          | Accepted |
| 0002 | [Keycloak as IdP from day 1](0002-keycloak-day-1.md)                         | Accepted |
| 0003 | [Database per service from day 1](0003-db-per-service.md)                    | Accepted |
| 0004 | [API versioning from day 1](0004-api-versioning.md)                          | Accepted |
| 0005 | [WebClient for BFF, OpenFeign for internal](0005-webclient-vs-feign.md)      | Accepted |
| 0006 | [CQRS in catalog-service](0006-cqrs-event-sourcing.md)                       | Accepted |
| 0007 | [Saga: coreografiada vs orquestada](0007-saga.md)                            | Accepted |
| 0008 | [Outbox Pattern para garantía transaccional](0008-outbox.md)                 | Accepted |
| 0009 | [Event Sourcing en playback-service](0009-event-sourcing.md)                 | Accepted |
| 0010 | [Conventional commits, semantic versioning](0010-versioning-commits.md)      | Accepted |
| 0011 | [Spring Cloud 2025.0.x with Boot 3.5](0011-spring-cloud-2025.md)             | Accepted |
| 0012 | [One content table with a type discriminator](0012-unified-content-model.md) | Accepted |
| 0013 | [Typed JSON cache values, no default typing](0013-cache-serialization.md)    | Accepted |
