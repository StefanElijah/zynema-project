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

## Phase 3 — Auth with Keycloak ✅

- [x] Realm `zynema` versioned with deterministic user ids, token lifetimes,
      audience mapper and three clients: `zynema-web` (public, PKCE),
      `zynema-cli` (dev/test, password grant) and `zynema-bff` (confidential)
- [x] Roles `user`, `admin`, `content-manager`; seeded users `demo`, `manager`
      and `admin`
- [x] Shared security starter in `common` (ADR-0014): JWT decoder,
      realm-role converter, JSON 401/403, secure-by-default chains
- [x] Split-horizon issuer + audience validation (ADR-0015); Keycloak moved to
      host port 8180 to free 8081 for user-service
- [x] Every service is a resource server; per-service authorization matrices
      (`catalog` public reads + role-gated admin, `user` self-service + admin,
      `auth` public config, gateway route rules)
- [x] `user-service`: self-service under `/api/v1/users/me/**` with JIT
      provisioning linked by Keycloak subject or verified email
- [x] Frontend: `oidc-client-ts` + `react-oidc-context`, PKCE, silent renew,
      session storage (ADR-0016), protected `/account` route, token-aware client
- [x] Tests: authorization matrices, a real-Keycloak integration test
      (issuer, audience, JWKS, role mapping) and the shared-starter tests

**Deferred on purpose:** self-service registration via the Keycloak Admin API.
It needs a service account with `manage-users`, a password policy and email
verification, which only make sense once notification-service and the
subscription flow exist (Phase 4/7).

## Phase 4 — Domain services and resilience ✅

- [x] `payment-service`: plans, subscriptions, payments and history, with
      idempotency keys reserved before execution (ADR-0017)
- [x] `playback-service`: playback sessions with one open session per
      profile+content, heartbeats, progress relay and a concurrency limit read
      from the plan's entitlements
- [x] OpenFeign between services with per-client timeouts, token relay and
      correlation/trace propagation (ADR-0019)
- [x] Resilience4j: `@Retry`/`@CircuitBreaker`/`@Bulkhead` composition with
      explicit degradation (cached identity, free-tier entitlements, best-effort
      progress) and `503` when the request cannot be authorised
- [x] Rate limiting at the gateway with the stock Redis token bucket behind a
      custom filter that keeps the `ApiError` contract (ADR-0018)
- [x] Deep health checks: `liveness` = process, `readiness` = own dependencies
      (`db`, `redis`), with the shared group extended per service in the config
      repo; Docker healthchecks point at `readiness`
- [x] Distributed tracing instrumented end-to-end (OpenTelemetry + Tempo):
      servlet, security and Feign spans carry the trace across the gateway and
      the services, with `traceparent` propagated by the shared interceptor
- [x] Dependency hygiene: BOM-managed versions restored, overrides only where
      justified (ADR-0020)
- [x] Tests: WireMock for downstream failure modes, real Postgres/Redis via
      Testcontainers, concurrency-safe rate limit tests, idempotency replay

**Lesson worth keeping:** the Config Server was serving nothing for three
phases — `spring.config.import: optional:configserver:` is silently ignored when
`spring-cloud-starter-config` is not on the classpath, so the shared properties
(tracing endpoint among them) never reached the services. Optional imports hide
missing dependencies; the fix and the runtime verification are recorded in the
runbook.

## Phase 5 — BFF (reactive) and CQRS ✅

- [x] `bff-service` with WebClient: one endpoint per screen under
      `/api/v1/web/**` (home, content detail, account, profile rails), with the
      view model owned by the BFF and the public/personal split at the edge and
      in the service (ADR-0021)
- [x] Aggregated response cache in Redis: whole screens with per-cache TTLs
      (home 5 min, detail 2 min, personal 60 s), cache aspect outside the
      resilience aspects so a cached screen survives an open circuit
- [x] Timeouts, fallbacks and per-dependency circuit breakers: token relay,
      correlation propagation, 4xx is not a failure, optional sections degrade
      with a `degraded` marker instead of lying (ADR-0021)
- [x] Watching requires an account and an active plan: playback answers
      `402 SUBSCRIPTION_REQUIRED` without one and `503` when the plan cannot be
      verified; the BFF only reports it
- [x] CQRS read model in `catalog-service`: `content_read_model` with columns
      for filter/sort/search and JSONB payloads, projected inside the write
      transaction, backfilled on first boot and rebuildable on demand
      (ADR-0022); reads never touch the write tables, genres excepted
- [x] WireMock-based tests for the BFF (composition, degradation, cache, token
      relay), resilience classification unit tests, and read-model tests proving
      that reads come from the projection and writes project atomically

**Lesson worth keeping:** annotations that live in aspects fail silently when
the aspect is missing. The BFF's `@CircuitBreaker` configuration looked correct
and did nothing until `spring-boot-starter-aop` was on the classpath — the same
family of bug as the missing Config client in Fase 4. When a cross-cutting
feature "does not seem to apply", check that its infrastructure is present
before debugging the configuration.

## Phase 6 — Video pipeline ✅

- [x] FFmpeg HLS multi-bitrate renditions (240p/480p/720p/1080p): one pass, four
      aligned renditions with forced keyframes, in a one-shot `video-worker`
      job (ADR-0023)
- [x] MinIO storage via AWS SDK (S3-compatible): private buckets, uploads and
      source import through the SDK, Chainguard image pinned by digest — the
      worker also replaces the `mc`-based bucket init
- [x] Nginx HLS delivery with CORS: the `/minio/` edge proxies MinIO with the
      host the signature was computed for (ADR-0024)
- [x] `playback-service` issues time-limited signed URLs: manifests rewritten
      per session (token + ownership) and a 60 s presigned URL per segment,
      with `409 CONTENT_NOT_READY` when the pipeline has not rendered a title
      (ADR-0024)
- [x] Frontend playback with hls.js: `/watch/:contentId` reads the title and the
      account from the BFF, starts a session on a click, heartbeats every 15 s
      and ends the session on the way out; every failure code has its own
      message (paywall, stream limit, not ready)
- [x] Creative-Commons test videos: `make fetch-samples` imports Blender open
      movie clips (with a synthetic fallback for offline work)

**Lesson worth keeping:** the infrastructure around a feature ages faster than
the code. MinIO's community images disappeared from their registries during this
phase and the official image now refuses to serve without a licence, so a plan
that assumed "MinIO in Docker" had to become "a source-built image, pinned by
digest, plus our own bucket initialisation" — and the runbook now records how to
re-pin it.

## Phase 7 — Async messaging (Kafka)

- [x] Topics: `zynema.payment.events`, `zynema.user.events`,
      `zynema.playback.events`, `zynema.notification.events` (one per
      aggregate, aggregate id as key) plus command topics for the orchestrated
      saga; created by `kafka-init` in the core profile (ADR-0025)
- [x] Contracts and serialisation: payload as the message value with its own
      JSON Schema in the registry (BACKWARD, `TopicRecordNameStrategy`), the
      envelope in headers, and `@RetryableTopic` + dead-letter topic per
      consumer (ADR-0025); `ProcessedEventStore` gives consumers idempotency
- [ ] Producers with the outbox pattern
- [ ] Consumers: notification-service sends email (MailHog in dev)
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
