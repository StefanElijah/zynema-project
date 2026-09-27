# ADR-0020: Version hygiene — let the BOM decide

- **Status:** Accepted
- **Date:** 2026-09-27
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 4 (domain services and resilience)

## Context

The parent POM had accumulated `<x.version>` properties that froze libraries the
Spring Boot BOM already manages: Micrometer Tracing, the OpenTelemetry exporter,
Flyway and Lombok. Each one had been added at some point to "pin" a version, and
by the time this was reviewed every one of them was **behind** the BOM's:

| Property                     | Pinned  | BOM manages | Consequence                |
| ---------------------------- | ------- | ----------- | -------------------------- |
| `micrometer-tracing.version` | 1.4.0   | 1.5.0       | stale tracing stack        |
| `opentelemetry.version`      | 1.43.0  | 1.49.0      | stale OTel SDK/exporter    |
| `flyway.version`             | 10.20.1 | 11.7.2      | a major release behind     |
| `lombok.version`             | 1.18.36 | 1.18.38     | stale annotation processor |

An override is a maintenance liability with no upside here: the BOM is the
combination Spring Boot 3.5 tests, and a pinned older version silently keeps
the service on an untested combination.

## Decision

- **Delete overrides that shadow the BOM** (the four above). Boot's version wins
  by default, which is the point.
- **Keep an override only when it is deliberate and documented**, with a reason
  that would not hold for the BOM's version. Two survive:
  - `testcontainers.version` — the Boot 3.5 BOM ships a release whose Docker API
    negotiation fails against the local Docker Desktop (API ≥ 1.44; Docker 29),
    so tests would not start;
  - `springdoc.version` — newer releases of the 2.x line fix OpenAPI generation
    we depend on.
- **Verify the bump as a change**, not as a formality: the four removals were
  followed by a full `mvn verify`, since Flyway is a major upgrade and tracing
  bridges are sensitive to version skew.

## Consequences

**Positive**

- The dependency set is the one the Boot release is tested with; upgrades arrive
  with Boot instead of being forgotten.
- Overrides now mean something: each remaining one has a written reason, so a
  reviewer can challenge it.
- Flyway moved from 10.20 to 11.7 with a green build, so the migration SQL was
  exercised against the newer engine.

**Negative**

- A Boot upgrade can move a library the project did not ask to move, so the
  full `mvn verify` is mandatory on every version bump.
- The two remaining overrides must be revisited when Boot 3.6 lands and the
  Docker/API mismatch is fixed upstream.

## Notes

- Part of the same review: the tracing dependencies themselves were left to the
  BOM (only the bridges/exporter are declared, unversioned) and the tracing
  endpoint was pinned as a literal in the Config Server repository, because a
  placeholder that the OTLP autoconfiguration cannot resolve fails at startup
  (see ADR-0009 and the Fase 4 runbook notes).
