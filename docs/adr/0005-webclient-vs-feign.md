# ADR-0005: WebClient para BFF, OpenFeign para servicios internos

- **Status:** Accepted
- **Date:** 2026-07-09
- **Deciders:** Project owner

## Context

We need to choose how services call each other. Spring offers two main
HTTP client options:

- **OpenFeign**: declarative, blocking, sync. Code is interfaces and
  annotations. Compile-time-safe. Mature, simple, easy to mock.
- **WebClient**: reactive, non-blocking, part of Project Reactor. Lets us
  fan out and aggregate without threads.

## Decision

We use **WebClient** for the **BFF** and **OpenFeign** for **inter-service
calls from domain services**.

- BFF: `bff-service` uses WebClient + WebFlux. It aggregates N calls
  concurrently and benefits from non-blocking I/O under load.
- Domain services (`payment`, `user`, `catalog`, etc.): use OpenFeign
  with `@FeignClient` interfaces. The calls are simpler, we don't have
  the operational cost of dealing with reactive types in business code.

## Rationale

- Netflix-style: reactive at the edge, declarative inside.
- WebClient shines when you have **fan-out / fan-in** patterns (BFF
  composing a screen from 5 services).
- OpenFeign shines when you have **simple point-to-point** calls (one
  service calling another for a specific business reason).
- Mixing them is OK because they live in different services.

## Consequences

- Resilience4j circuit breakers are configured per-call. Both WebClient
  and OpenFeign can use them.
- The BFF is fully reactive end-to-end (WebFlux + WebClient). The
  frontend is not, but the gateway terminates the reactive stream at the
  edge.
- OpenFeign clients use the same `application.yml` config to talk to
  Eureka for service URLs.

## Trade-offs accepted

- Two HTTP client APIs in the codebase. The split is clear from the
  service name.
- WebClient is more complex for engineers new to Reactor. We add
  documentation in `docs/learning/reactive-streams.md` (TODO).
