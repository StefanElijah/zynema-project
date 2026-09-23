# Roadmap

The build is organised in 10 phases. Each phase ends with a green
`mvn -B verify`, a green `docker compose config`, and a conventional commit
on `develop`. Phases are sequential: a phase only starts when the previous
one is verified end-to-end.

Status legend: `[x]` done · `[~]` in progress · `[ ]` pending

---

## Phase 0 — Monorepo bootstrap ✅

- [x] Multi-stack monorepo (Nx 22 + pnpm workspaces, Maven multi-module)
- [x] Frontend migrated from CRA to Vite + React 19 + Tailwind v4
- [x] Root tooling: `.npmrc`, `package.json` (pnpm-only preinstall hook),
      EditorConfig, Husky, lint-staged, commitlint
- [x] `docker-compose.yml` at root with profiles (core / auth / storage / observability)
- [x] Infra configs: Prometheus, Grafana, Loki, Tempo, Promtail, Nginx, Keycloak realm, FFmpeg scripts
- [x] CI workflows: backend, frontend, observability, commitlint
- [x] Docs: C4 (levels 1–4), 10 ADRs, runbooks, learning notes

## Phase 1 — Service discovery, config and gateway ✅

- [x] Eureka Server, Config Server and API Gateway boot and register with each other
- [x] Gateway routing: 9 static routes + Eureka auto-discovered routes; 503/404 semantics verified
- [x] `common` turned into a real auto-configured library (servlet + reactive)
      with `CorrelationIdFilter`/`WebFilter` and both exception handlers
- [x] Spring Cloud **2025.0.3** with Boot 3.5 (ADR-0011) after the 2024.0.x incompatibility
- [x] springdoc pinned to 2.8.14 (2.8.15+ WebFlux regression)
- [x] Docker builds fixed (root build context, Maven image, BuildKit cache, `wget` healthchecks)
- [x] 9 context/smoke tests green

## Phase 2 — Persistence and first domain ✅

- [x] Repaired 5 malformed `application.yml` files; every service now has a
      Testcontainers-backed context test
- [x] Husky hooks wired (pre-commit → lint-staged, commit-msg → commitlint)
- [x] `catalog-service`: Flyway V1–V3, 52 titles seeded (28 movies, 24 series,
      15 genres, 72 people, 80 credits, 80 seasons, 48 episodes),
      REST `/api/v1/catalog/**`, typed-JSON Redis cache (ADR-0013),
      36 tests
- [x] `user-service`: Flyway V1–V2 (users, profiles, watchlist, watch history),
      REST `/api/v1/users/**`, idempotent progress upsert, 33 tests
- [x] Gateway routes aligned with the versioned public API and verified
      end-to-end against real services, real PostgreSQL and real Redis

## Phase 3 — Auth with Keycloak

- [ ] Keycloak realm `zynema` exported and versioned
- [ ] Clients: `zynema-web` (public, PKCE) and `zynema-bff` (confidential)
- [ ] Roles: `user`, `admin`, `content-manager`
- [ ] `auth-service` as JWT resource server; resource-server config re-enabled
      in every service (removed in Phase 2 on purpose, see ADR-0002)
- [ ] Spring Security at the gateway; frontend PKCE flow
- [ ] Testcontainers Keycloak tests

## Phase 4 — Domain services and resilience

- [ ] `payment-service` with idempotency keys
- [ ] `playback-service` with playback sessions
- [ ] OpenFeign between services with timeouts and fallbacks
- [ ] Resilience4j: circuit breakers, retries, bulkheads
- [ ] Rate limiting at the gateway (Bucket4j + Redis)
- [ ] Deep health checks (DB, Redis, Kafka, Eureka, Keycloak)
- [ ] Distributed tracing instrumented (OpenTelemetry)

## Phase 5 — BFF (reactive) and CQRS

- [ ] `bff-service` with WebClient: API composition for the frontend
- [ ] Aggregated response cache in Redis
- [ ] Timeouts, fallbacks and per-dependency circuit breakers
- [ ] CQRS read model in `catalog-service` (projection + materialised view)
- [ ] WireMock-based tests

## Phase 6 — Video pipeline

- [ ] FFmpeg HLS multi-bitrate renditions (240p/480p/720p/1080p)
- [ ] MinIO storage via AWS SDK (S3-compatible)
- [ ] Nginx HLS delivery with CORS
- [ ] `playback-service` issues time-limited signed URLs
- [ ] Frontend playback with hls.js
- [ ] Creative-Commons test videos

## Phase 7 — Async messaging (Kafka)

- [ ] Topics: `playback-events`, `user-events`, `payment-events`, `notification-events`
- [ ] Producers/consumers with schema contracts and DLQ handling
- [ ] Choreographed saga: subscription created → notification sent (with compensation)
- [ ] Orchestrated saga comparison
- [ ] Outbox pattern in `payment-service`
- [ ] Event sourcing experiment in `playback-service`
- [ ] `notification-service` consumes and sends email (MailHog in dev)

## Phase 8 — Frontend complete

- [ ] TypeScript client generated from the BFF OpenAPI spec
- [ ] TanStack Query + Zustand wired to the BFF
- [ ] Pages: home, catalog, detail, player, profiles, search, my list, settings
- [ ] Keycloak login/refresh/logout
- [ ] hls.js player with custom controls
- [ ] React Hook Form + Zod forms
- [ ] shadcn/ui component layer
- [ ] Playwright E2E

## Phase 9 — Observability end-to-end

- [ ] Prometheus scrapes every service (via Eureka where possible)
- [ ] Grafana datasources: Prometheus, Loki, Tempo
- [ ] Per-service dashboards (JVM, latency, error rate, RPS)
- [ ] Distributed traces end-to-end
- [ ] Centralised logs (Loki + Promtail)
- [ ] Alerts: service down, error rate > 5%, p99 > 2s

## Phase 10 — CI/CD, quality and polish

- [ ] GitHub Actions matrices for backend and frontend
- [ ] JaCoCo coverage per module, SonarQube Cloud gate on PRs
- [ ] OpenAPI specs published as CI artefacts
- [ ] Semantic release + generated CHANGELOG
- [ ] C4 diagrams refreshed; K8s manifests documented
- [ ] 5-minute demo script

---

## Architecture decision records

See [`docs/adr/README.md`](../adr/README.md) for the full index. Every
significant decision lands as an ADR **before** the code that implements it.
