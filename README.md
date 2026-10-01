# Zynema

> Production-grade streaming platform simulation, locally runnable. Netflix-style microservice architecture with the same pieces you'd use in real production — only _where_ they live changes, not _what_ they are.

[![Backend CI](https://github.com/StefanElijah/zynema-project/actions/workflows/backend-ci.yml/badge.svg?branch=main)](https://github.com/StefanElijah/zynema-project/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/StefanElijah/zynema-project/actions/workflows/frontend-ci.yml/badge.svg?branch=main)](https://github.com/StefanElijah/zynema-project/actions/workflows/frontend-ci.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=StefanElijah_zynema-project&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=StefanElijah_zynema-project)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=StefanElijah_zynema-project&metric=coverage)](https://sonarcloud.io/summary/new_code?id=StefanElijah_zynema-project)
[![Release](https://img.shields.io/github/v/release/StefanElijah/zynema-project?label=release)](https://github.com/StefanElijah/zynema-project/releases)

**Status:** all ten phases delivered, current release **v1.0.6**, SonarCloud quality gate green at **90.6% coverage** with **0 open issues**.

---

## ⚠️ Package manager: pnpm exclusively

This project uses **pnpm**. Do **not** use `npm` or `yarn`. A preinstall hook will fail-fast if `npm` is detected.

```bash
pnpm install        # install everything
pnpm dev            # run dev servers
pnpm build          # build everything
pnpm test           # run all tests
pnpm lint           # lint everything
pnpm format         # format everything
```

---

## Table of contents

- [Architecture overview](#architecture-overview)
- [Tech stack](#tech-stack)
- [Repository layout](#repository-layout)
- [Quick start](#quick-start)
- [Available commands](#available-commands)
- [Local profiles (memory-aware)](#local-profiles-memory-aware)
- [Local → production mapping](#local--production-mapping)
- [Documentation](#documentation)
- [Branching strategy](#branching-strategy)
- [Roadmap](#roadmap)
- [License](#license)

---

## Architecture overview

```
        ┌─────────────────────────────────────────────────┐
        │  CLIENTS:  Web SPA (Vite · React 19 · hls.js)   │
        └───────────────────────┬─────────────────────────┘
                                ▼
        ┌─────────────────────────────────────────────────┐
        │  API GATEWAY  (Spring Cloud Gateway, WebFlux)   │
        └─┬───────────┬───────────┬───────────┬───────────┬─┘
          ▼           ▼           ▼           ▼           ▼
      ┌──────┐   ┌─────────┐  ┌─────────┐ ┌─────────┐  ┌───────┐
      │ AUTH │   │ CATALOG │  │ PAYMENT │ │PLAYBACK │  │  BFF  │
      └──────┘   └─────────┘  └─────────┘ └─────────┘  └───────┘

      The BFF composes the four domains over WebClient and owns the
      SPA's screens (/api/v1/web/**); the rest talks HTTP via OpenFeign.

                            ┌──────────────────────────┐
                            │  EUREKA  (discovery)     │
                            │  CONFIG  (config repo)   │
                            └────────────┬─────────────┘
                                         │
                       ┌─────────────────┼───────────────────┐
                       ▼                 ▼                   ▼
                 ┌──────────┐      ┌──────────┐        ┌──────────┐
                 │ Postgres │      │  Redis   │        │  Kafka   │
                 │  (DBPS)  │      │  (cache) │        │ (events) │
                 └──────────┘      └──────────┘        └────┬─────┘
                                                            ▼
   Async: notification-service (consumers + dead letters) ·
   video-worker (FFmpeg HLS, one-shot) · MinIO + nginx edge

  Cross-cutting:
  • Resilience4j (circuit breakers, retries, bulkheads) · OpenFeign + WebClient
  • OpenTelemetry → Tempo (traces, span metrics, service graph)
  • Prometheus + Grafana + Loki + Alertmanager (metrics, logs, alerts)
  • Keycloak (OIDC) · Kafka + JSON Schema Registry (outbox, sagas)
  • MinIO + FFmpeg + Nginx (video pipeline)
```

Read the full design in [`docs/architecture/`](docs/architecture/) and decision records in [`docs/adr/`](docs/adr/).

---

## Tech stack

| Layer              | Tech                                                             | Why                                             |
| ------------------ | ---------------------------------------------------------------- | ----------------------------------------------- |
| **Frontend**       | Vite · React 19 · TypeScript                                     | CRA is deprecated, Vite is the modern standard  |
| **State (server)** | TanStack Query                                                   | Async cache, revalidation, no Redux boilerplate |
| **State (client)** | Zustand                                                          | Lightweight UI state                            |
| **Styling**        | Tailwind CSS · shadcn/ui                                         | Utility-first + accessible components           |
| **Forms**          | React Hook Form · Zod                                            | Type-safe validation                            |
| **API client**     | Auto-generated from OpenAPI                                      | Contract-first, no drift                        |
| **Video**          | hls.js                                                           | Native HLS playback in browser                  |
| **Backend**        | Java 21 · Spring Boot 3.5.x                                      | Industry standard for microservicios            |
| **Discovery**      | Netflix Eureka                                                   | Service registry                                |
| **Config**         | Spring Cloud Config Server                                       | External, versioned config in Git               |
| **Gateway**        | Spring Cloud Gateway                                             | Reactive routing + filters                      |
| **HTTP clients**   | OpenFeign (sync) · WebClient (BFF)                               | Declarative + reactive                          |
| **Resilience**     | Resilience4j                                                     | Circuit breaker, retry, bulkhead, ratelimit     |
| **Auth**           | Keycloak (OIDC) · Spring Security                                | Real IdP, OAuth2 + JWT                          |
| **Database**       | PostgreSQL · Spring Data JPA · Flyway                            | Per-service databases, versioned migrations     |
| **Cache**          | Redis                                                            | Sessions, rate limit, hot data                  |
| **Messaging**      | Apache Kafka (KRaft) · Spring Kafka                              | Event-driven, no Zookeeper                      |
| **Schemas**        | JSON Schema (Avro-ready)                                         | Event contracts, evolution                      |
| **Patterns**       | Saga · Outbox · CQRS · Event Sourcing                            | Distributed transaction patterns                |
| **Observability**  | Micrometer · Prometheus · Grafana · Loki · Tempo · OpenTelemetry | Full PLG+T stack                                |
| **Video pipeline** | FFmpeg · MinIO · Nginx                                           | Transcode, store, serve HLS                     |
| **CI/CD**          | GitHub Actions                                                   | Path-filtered per stack                         |
| **Quality**        | JaCoCo · SonarQube Cloud                                         | Coverage, code smells, vulnerabilities          |
| **Monorepo**       | Nx · pnpm workspaces                                             | Multi-stack task graph                          |
| **Container**      | Docker Compose (profiles)                                        | Local production simulation                     |

---

## Repository layout

```
.
├── .github/             # GitHub Actions workflows
├── .vscode/             # Editor settings (committed for team)
├── backend/             # Java 21 + Spring Boot multi-module Maven
│   ├── common/          # Shared DTOs, exceptions, utils
│   ├── eureka-server/   # Service discovery
│   ├── config-server/   # External config
│   ├── api-gateway/     # Routing, auth, ratelimit
│   ├── auth-service/    # JWT validation against Keycloak
│   ├── user-service/    # User profiles
│   ├── catalog-service/ # Movies, series, metadata
│   ├── payment-service/ # Subscriptions (with Outbox + Saga)
│   ├── playback-service/# Playback sessions (Event Sourcing)
│   ├── bff-service/     # Reactive aggregator for the frontend
│   ├── notification-service/ # Email + Kafka consumer
│   ├── video-worker/    # One-shot FFmpeg HLS transcoder (ADR-0023)
│   └── event-contracts/ # Shared event schemas
├── frontend/            # Vite + React 19 + TypeScript
├── libs/                # Shared TypeScript libraries
│   └── api-contracts/   # OpenAPI-generated types
├── infra/               # Infrastructure as code
│   ├── docker/          # Dockerfiles for custom services
│   ├── observability/   # Prometheus, Alertmanager, Grafana, Loki, Tempo configs
│   ├── keycloak/        # Realm export
│   ├── nginx/           # HLS serving config
│   ├── ffmpeg/          # Transcoding scripts
│   ├── k8s/             # Kubernetes manifests (documented, not deployed)
│   ├── config-repo/     # Git-tracked config for Config Server
│   └── scripts/         # Seed, transcode, init scripts
├── docs/                # Architecture, ADRs, runbooks, learning notes
├── docker-compose.yml   # Local stack definition (root, per docs)
├── docker-compose.override.yml # Dev-only debug ports
├── Makefile             # Quick commands
├── nx.json              # Nx monorepo task graph
├── pnpm-workspace.yaml  # Frontend workspace
├── package.json         # Root scripts (pnpm only)
├── tsconfig.base.json   # Shared TypeScript config
├── .env.example         # All env vars documented
├── .editorconfig
├── .gitignore
├── .nvmrc               # Node 24 (CI pins 22)
└── .sdkmanrc            # Java 21, Maven 3.9.9
```

---

## Quick start

### Prerequisites

| Tool           | Version | Check              |
| -------------- | ------- | ------------------ |
| Node.js        | 22+     | `node --version`   |
| pnpm           | 9+      | `pnpm --version`   |
| Java           | 21      | `java --version`   |
| Maven          | 3.9+    | `mvn --version`    |
| Docker Desktop | 4.x+    | `docker --version` |

> macOS / Linux users can use [SDKMAN](https://sdkman.io/) to install Java and Maven. The `.sdkmanrc` pins the versions used by this project.

### First run

```bash
# 1. Clone and enter
git clone <your-fork-url> zynema-project
cd zynema-project
git checkout develop

# 2. Create .env from template
make env

# 3. Edit .env with your secrets (Keycloak admin, MinIO, etc.)
$EDITOR .env

# 4. Install frontend deps
pnpm install

# 5. Boot the core stack (Eureka, Config, Gateway, DBs, Kafka)
make up-core

# 6. Boot auth (Keycloak + MailHog)
make up          # core + auth

# 7. Open dashboards
# Eureka:    http://localhost:8761
# Keycloak:  http://localhost:8180  (admin / $KEYCLOAK_ADMIN_PASSWORD)
```

### Optional profiles

```bash
make up-full      # core + auth + storage + observability (~5GB RAM)
```

### What is already in the database

Flyway seeds the catalog and one demo account on first boot, so the API is
usable immediately:

| Data                               | Count                                   |
| ---------------------------------- | --------------------------------------- |
| Genres                             | 15                                      |
| Titles (28 movies + 24 series)     | 52                                      |
| People / credits                   | 72 / 80                                 |
| Seasons / episodes                 | 80 / 48                                 |
| Seeded account (`demo@zynema.dev`) | 1 user, 2 profiles, watchlist + history |

The 25 titles that ship with local artwork under `frontend/public/assets/`
carry real poster/backdrop paths; the rest have `null` assets on purpose, which
exercises the frontend's fallback handling.

## Authentication

Keycloak is the identity provider from the first commit (ADR-0002). The realm
is versioned at `infra/keycloak/realm-export/zynema-realm.json` and imported on
container start, so `docker compose --profile core --profile auth up -d` gives
you a working IdP plus three users.

| User      | Password  | Realm roles               | Use                                          |
| --------- | --------- | ------------------------- | -------------------------------------------- |
| `demo`    | `demo`    | `user`                    | Default viewer, linked to the seeded account |
| `manager` | `manager` | `user`, `content-manager` | Can manage the catalog                       |
| `admin`   | `admin`   | `user`, `admin`           | Full access, including user administration   |

> Dev credentials. They exist only in the exported realm; a real deployment
> provisions users through the IdP and never ships passwords in a repository.

**How a request is authenticated**

1. The SPA (public client, authorization code + PKCE) obtains tokens from
   Keycloak at `http://localhost:8180/realms/zynema`.
2. It sends the access token to the gateway as `Authorization: Bearer …`.
3. The gateway validates the token and enforces route rules.
4. Each service validates the token again and applies its own rules — a request
   that bypasses the gateway is not trusted.

**Authorization matrix**

| Endpoint                                                      | Rule                                                                |
| ------------------------------------------------------------- | ------------------------------------------------------------------- |
| `GET /api/v1/catalog/**`                                      | anonymous                                                           |
| `/api/v1/catalog/admin/**`                                    | role `content-manager` or `admin`                                   |
| `/api/v1/auth/public/**`                                      | anonymous                                                           |
| `/api/v1/auth/me`                                             | authenticated                                                       |
| `/api/v1/users/me`, `/api/v1/users/me/**`                     | authenticated (identity from the token)                             |
| `/api/v1/users/**` (id-addressed)                             | role `admin`                                                        |
| `/api/v1/web/home`, `/api/v1/web/catalog/**` (BFF reads)      | public                                                              |
| `/api/v1/web/account`, `/api/v1/web/profiles/**` (BFF)        | authenticated                                                       |
| `GET /api/v1/playback/stream/**` (HLS manifests)              | authenticated + session ownership                                   |
| `/api/v1/playback/**`                                         | authenticated; watching also needs an active plan (`402` otherwise) |
| `/actuator/health`, `/actuator/prometheus`, `/v3/api-docs/**` | anonymous                                                           |

Errors use the same `ApiError` envelope as the rest of the API: a missing token
is `401`, a valid token without the required role is `403`, and a signed-in
account without an active plan gets `402` when it tries to watch something.

**Watching a title (Fase 6)**

```bash
# 1. Put videos in the source bucket (Creative-Commons clips, or synthetic ones)
make up-storage fetch-samples

# 2. Transcode the demo movie and one Arcane episode into HLS
make transcode

# 3. From the app: /watch/dune-part-two (the player starts a session and plays)
#    By hand: start a session, then follow its streamPath
curl -X POST localhost:8080/api/v1/playback/sessions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"profileId":"<profile-id>","contentId":"<content-id>","device":"curl"}'
```

Segments are presigned for 60 seconds and served by the nginx edge
(`http://localhost:8090/minio/...`); manifests require the token and session
ownership (ADR-0024).

### Getting a token for manual testing

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/zynema/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=zynema-cli -d client_secret=zynema-cli-dev-secret \
  -d username=demo -d password=demo \
  -d scope="openid profile email" | jq -r .access_token)
```

### Verifying the stack end to end

```bash
# Public reads through the gateway (anonymous)
curl "http://localhost:8080/api/v1/catalog/movies?size=3"
curl "http://localhost:8080/api/v1/catalog/series/arcane"
curl "http://localhost:8080/api/v1/catalog/series/arcane/seasons/1/episodes"
curl "http://localhost:8080/api/v1/catalog/search?q=dune"

# Identity-required calls
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/users/me"
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/auth/me"

# Role-gated write (needs a manager or admin token)
curl -X POST -H "Authorization: Bearer $MANAGER_TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"MOVIE","title":"Example","slug":"example","genreSlugs":["drama"]}' \
  "http://localhost:8080/api/v1/catalog/admin/contents"
```

Expected: 200/201 as appropriate, `401` without a token, `403` with a token
that lacks the role, `404` for an unknown slug, `400` for `?size=500`, and
`503` when a routed service is not running. Each service also exposes its own
OpenAPI spec at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

---

## Available commands

```bash
make help         # full command list
make check        # verify tooling
make up-core      # start core only (~2GB)
make up           # start core + auth (~2.5GB)
make up-storage   # start MinIO + nginx-hls and create the buckets
make up-observability # start Prometheus, Alertmanager, Grafana, Loki, Tempo
make up-full      # start everything (~5GB)
make refresh-targets  # regenerate Prometheus targets from Eureka
make down         # stop (keeps volumes)
make clean        # stop + delete volumes
make logs         # tail all logs
make logs-gateway # tail one service
make storage-init # create the pipeline buckets (idempotent)
make fetch-samples # import the Creative-Commons demo videos
make transcode    # transcode demo videos
make dev-fe       # run Vite dev server
make build-be     # build all backend modules
make test-be      # run all backend tests
make test-fe      # run frontend tests
make lint         # lint all
make format       # format all
make nx-graph     # visualise Nx task graph
```

---

## Local profiles (memory-aware)

Boot only what you need to fit your machine:

| Profile           | Services                                                                            | ~RAM   |
| ----------------- | ----------------------------------------------------------------------------------- | ------ |
| `core`            | eureka, config, gateway, postgres, redis, kafka, schema-registry, 5 domain services | 2.0GB  |
| `+ auth`          | keycloak, mailhog                                                                   | +0.6GB |
| `+ storage`       | minio, nginx-hls                                                                    | +0.2GB |
| `+ observability` | prometheus, alertmanager, grafana, loki, tempo, promtail                            | +0.6GB |

With 16GB of system RAM you can run **all profiles simultaneously** and still have ~10GB for your IDE and browser.

---

## Local → production mapping

This table is your interview cheat-sheet: each local component maps 1:1 to a managed service in real production.

| Local (this project)   | Production real (AWS / GCP / Azure)                 |
| ---------------------- | --------------------------------------------------- |
| Docker Compose         | Kubernetes (EKS / GKE / AKS)                        |
| Eureka                 | Kubernetes Service Discovery (or Consul)            |
| Config Server (Git)    | Spring Cloud Config + Vault, or AWS Parameter Store |
| PostgreSQL (container) | RDS / Cloud SQL / Azure Database                    |
| Redis (container)      | ElastiCache / Memorystore / Azure Cache             |
| Kafka (container)      | MSK / Confluent Cloud / EventBridge                 |
| MinIO                  | S3 / GCS / Azure Blob                               |
| Nginx serving HLS      | CloudFront / Cloudflare CDN                         |
| Prometheus + Grafana   | Grafana Cloud / Datadog / New Relic                 |
| Alertmanager           | PagerDuty / Opsgenie / Grafana OnCall               |
| Loki + Tempo           | Managed Loki / Grafana Tempo / Honeycomb            |
| Keycloak (self-hosted) | Auth0 / Cognito / Okta                              |
| GitHub Actions runners | Self-hosted runners / GitLab CI                     |
| MailHog                | SES / SendGrid / Postmark                           |
| SonarQube Cloud        | SonarQube Server / Snyk / Veracode                  |

The application code is the same. Only the deployment target moves.

---

## Documentation

- [`docs/architecture/`](docs/architecture/) — C4 diagrams (context, containers, components, code)
- [`docs/adr/`](docs/adr/) — Architecture Decision Records (why we chose what)
- [`docs/api/`](docs/api/) — OpenAPI specs (generated by springdoc-openapi per service)
- [`docs/runbooks/`](docs/runbooks/) — Onboarding, troubleshooting, disaster recovery
- [`docs/learning/`](docs/learning/) — Deep dives on patterns (Saga, Outbox, CQRS, Event Sourcing)
- [`docs/demo-script.md`](docs/demo-script.md) — Five-minute walkthrough for demos and interviews

---

## Branching strategy

- `main` — production-ready, protected. Releases only.
- `develop` — integration branch, default for daily work.
- `feature/*` — short-lived feature branches off `develop`.
- `release/*` — release-candidate branches off `develop`, merged to `main` + `develop`.
- `hotfix/*` — emergency fixes off `main`, merged back to `main` + `develop`.

Conventional Commits enforced by `commitlint`. Pre-commit hooks (Husky) run Prettier on staged files.

---

## Roadmap

All ten phases are delivered and released (current: **v1.0.6**). See
[`docs/architecture/00-roadmap.md`](docs/architecture/00-roadmap.md) for the full
plan, the per-phase details and the lessons kept from each one, and
[`CHANGELOG.md`](https://github.com/StefanElijah/zynema-project/blob/main/CHANGELOG.md)
for the release history.

- [x] Phase 0 — Monorepo bootstrap
- [x] Phase 1 — Eureka, Config Server, API Gateway skeleton
- [x] Phase 2 — PostgreSQL + Flyway + catalog and user domains
- [x] Phase 3 — Auth with Keycloak (OIDC + JWT)
- [x] Phase 4 — Domain services + Resilience4j + rate limiting
- [x] Phase 5 — BFF reactive with WebClient + API composition + CQRS
- [x] Phase 6 — Video pipeline (FFmpeg + MinIO + Nginx)
- [x] Phase 7 — Kafka events + Saga + Outbox + Event Sourcing
- [x] Phase 8 — Frontend complete (TanStack Query, Zustand, hls.js, shadcn/ui)
- [x] Phase 9 — Observability end-to-end (Prometheus, Grafana, Loki, Tempo, OpenTelemetry)
- [x] Phase 10 — CI/CD, SonarQube, CHANGELOG, Kubernetes manifests

**Post-roadmap hardening:** the project is above the 90% line on SonarCloud
(90.6% overall, frontend at 99.3%), the quality gate is green with zero open
issues, and every workflow — backend matrix, frontend matrix + per-browser E2E,
OpenAPI drift check, observability validation and semantic release — runs on
`main` and on pull requests.

---

## License

The source is public for portfolio and study purposes. No license is granted
for reuse, redistribution or derivative works — all rights reserved.
