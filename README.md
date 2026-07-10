# Zynema

> Production-grade streaming platform simulation, locally runnable. Netflix-style microservice architecture with the same pieces you'd use in real production — only *where* they live changes, not *what* they are.

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
        ┌───────────────────────────────────────────────┐
        │  CLIENTS:  Web (Vite+React)  ·  Mobile (RN)  │
        └───────────────────┬───────────────────────────┘
                            ▼
        ┌───────────────────────────────────────────────┐
        │  API GATEWAY  (Spring Cloud Gateway, WebFlux) │
        └─┬───────────┬───────────┬──────────┬──────────┘
          ▼           ▼           ▼          ▼
       ┌──────┐  ┌────────┐  ┌────────┐ ┌─────────┐
       │ AUTH │  │ CATALOG│  │ PAYMENT│ │ PLAYBACK│
       └──┬───┘  └───┬────┘  └────┬───┘ └─────┬───┘
          │          │            │           │
          └──────────┴─────┬──────┴───────────┘
                            ▼
              ┌──────────────────────────┐
              │  EUREKA  (discovery)     │
              │  CONFIG  (config repo)   │
              └──────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
  ┌──────────┐       ┌──────────┐        ┌──────────┐
  │ Postgres │       │   Redis  │        │  Kafka   │
  │  (DBPS)  │       │  (cache) │        │ (events) │
  └──────────┘       └──────────┘        └──────────┘

  Cross-cutting:
  • Resilience4j (circuit breakers, retries, bulkheads)
  • OpenFeign (inter-service HTTP) + WebClient (BFF reactive)
  • OpenTelemetry → Tempo (distributed tracing)
  • Prometheus + Grafana + Loki (metrics, logs, dashboards)
  • Keycloak (OIDC identity provider)
  • MinIO + FFmpeg + Nginx (video pipeline)
```

Read the full design in [`docs/architecture/`](docs/architecture/) and decision records in [`docs/adr/`](docs/adr/).

---

## Tech stack

| Layer | Tech | Why |
|---|---|---|
| **Frontend** | Vite · React 18 · TypeScript | CRA is deprecated, Vite is the modern standard |
| **State (server)** | TanStack Query | Async cache, revalidation, no Redux boilerplate |
| **State (client)** | Zustand | Lightweight UI state |
| **Styling** | Tailwind CSS · shadcn/ui | Utility-first + accessible components |
| **Forms** | React Hook Form · Zod | Type-safe validation |
| **API client** | Auto-generated from OpenAPI | Contract-first, no drift |
| **Video** | hls.js | Native HLS playback in browser |
| **Backend** | Java 21 · Spring Boot 3.5.x | Industry standard for microservicios |
| **Discovery** | Netflix Eureka | Service registry |
| **Config** | Spring Cloud Config Server | External, versioned config in Git |
| **Gateway** | Spring Cloud Gateway | Reactive routing + filters |
| **HTTP clients** | OpenFeign (sync) · WebClient (BFF) | Declarative + reactive |
| **Resilience** | Resilience4j | Circuit breaker, retry, bulkhead, ratelimit |
| **Auth** | Keycloak (OIDC) · Spring Security | Real IdP, OAuth2 + JWT |
| **Database** | PostgreSQL · Spring Data JPA · Flyway | Per-service databases, versioned migrations |
| **Cache** | Redis | Sessions, rate limit, hot data |
| **Messaging** | Apache Kafka (KRaft) · Spring Kafka | Event-driven, no Zookeeper |
| **Schemas** | JSON Schema (Avro-ready) | Event contracts, evolution |
| **Patterns** | Saga · Outbox · CQRS · Event Sourcing | Distributed transaction patterns |
| **Observability** | Micrometer · Prometheus · Grafana · Loki · Tempo · OpenTelemetry | Full PLG+T stack |
| **Video pipeline** | FFmpeg · MinIO · Nginx | Transcode, store, serve HLS |
| **CI/CD** | GitHub Actions | Path-filtered per stack |
| **Quality** | JaCoCo · SonarQube Cloud | Coverage, code smells, vulnerabilities |
| **Monorepo** | Nx · pnpm workspaces | Multi-stack task graph |
| **Container** | Docker Compose (profiles) | Local production simulation |

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
│   └── event-contracts/ # Shared event schemas
├── frontend/            # Vite + React 18 + TypeScript
├── libs/                # Shared TypeScript libraries
│   └── api-contracts/   # OpenAPI-generated types
├── infra/               # Infrastructure as code
│   ├── docker/          # Dockerfiles for custom services
│   ├── observability/   # Prometheus, Grafana, Loki, Tempo configs
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
├── .nvmrc               # Node 20+
└── .sdkmanrc            # Java 21, Maven 3.9.9
```

---

## Quick start

### Prerequisites

| Tool | Version | Check |
|---|---|---|
| Node.js | 20+ | `node --version` |
| pnpm | 9+ | `pnpm --version` |
| Java | 21 | `java --version` |
| Maven | 3.9+ | `mvn --version` |
| Docker Desktop | 4.x+ | `docker --version` |

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
# Keycloak:  http://localhost:8081  (admin / $KEYCLOAK_ADMIN_PASSWORD)
```

### Optional profiles

```bash
make up-full      # core + auth + storage + observability (~5GB RAM)
```

---

## Available commands

```bash
make help         # full command list
make check        # verify tooling
make up-core      # start core only (~2GB)
make up           # start core + auth (~2.5GB)
make up-full      # start everything (~5GB)
make down         # stop (keeps volumes)
make clean        # stop + delete volumes
make logs         # tail all logs
make logs-gateway # tail one service
make seed         # load demo data
make transcode    # transcode demo videos
make dev-fe       # run Vite dev server
make build        # build everything
make test         # run all tests
make lint         # lint all
make format       # format all
make nx-graph     # visualise Nx task graph
```

---

## Local profiles (memory-aware)

Boot only what you need to fit your machine:

| Profile | Services | ~RAM |
|---|---|---|
| `core` | eureka, config, gateway, postgres, redis, kafka, schema-registry, 5 domain services | 2.0GB |
| `+ auth` | keycloak, mailhog | +0.6GB |
| `+ storage` | minio, nginx-hls | +0.2GB |
| `+ observability` | prometheus, grafana, loki, tempo, promtail | +0.6GB |

With 16GB of system RAM you can run **all profiles simultaneously** and still have ~10GB for your IDE and browser.

---

## Local → production mapping

This table is your interview cheat-sheet: each local component maps 1:1 to a managed service in real production.

| Local (this project) | Production real (AWS / GCP / Azure) |
|---|---|
| Docker Compose | Kubernetes (EKS / GKE / AKS) |
| Eureka | Kubernetes Service Discovery (or Consul) |
| Config Server (Git) | Spring Cloud Config + Vault, or AWS Parameter Store |
| PostgreSQL (container) | RDS / Cloud SQL / Azure Database |
| Redis (container) | ElastiCache / Memorystore / Azure Cache |
| Kafka (container) | MSK / Confluent Cloud / EventBridge |
| MinIO | S3 / GCS / Azure Blob |
| Nginx serving HLS | CloudFront / Cloudflare CDN |
| Prometheus + Grafana | Grafana Cloud / Datadog / New Relic |
| Loki + Tempo | Managed Loki / Grafana Tempo / Honeycomb |
| Keycloak (self-hosted) | Auth0 / Cognito / Okta |
| GitHub Actions runners | Self-hosted runners / GitLab CI |
| MailHog | SES / SendGrid / Postmark |
| SonarQube Cloud | SonarQube Server / Snyk / Veracode |

The application code is the same. Only the deployment target moves.

---

## Documentation

- [`docs/architecture/`](docs/architecture/) — C4 diagrams (context, containers, components, code)
- [`docs/adr/`](docs/adr/) — Architecture Decision Records (why we chose what)
- [`docs/api/`](docs/api/) — OpenAPI specs (generated by springdoc-openapi per service)
- [`docs/runbooks/`](docs/runbooks/) — Onboarding, troubleshooting, disaster recovery
- [`docs/learning/`](docs/learning/) — Deep dives on patterns (Saga, Outbox, CQRS, Event Sourcing)

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

This project is being built in 10 phases. See [`docs/architecture/00-roadmap.md`](docs/architecture/00-roadmap.md) for the full plan.

- [x] Phase 0 — Monorepo bootstrap (this commit)
- [ ] Phase 1 — Eureka, Config Server, API Gateway skeleton
- [ ] Phase 2 — PostgreSQL + Flyway + first domain (catalog)
- [ ] Phase 3 — Auth with Keycloak (OIDC + JWT)
- [ ] Phase 4 — Domain services + Resilience4j + rate limiting
- [ ] Phase 5 — BFF reactive with WebClient + API composition + CQRS
- [ ] Phase 6 — Video pipeline (FFmpeg + MinIO + Nginx)
- [ ] Phase 7 — Kafka events + Saga + Outbox + Event Sourcing
- [ ] Phase 8 — Frontend complete (TanStack Query, Zustand, hls.js, shadcn/ui)
- [ ] Phase 9 — Observability end-to-end (Prometheus, Grafana, Loki, Tempo, OpenTelemetry)
- [ ] Phase 10 — CI/CD, SonarQube, CHANGELOG, Kubernetes manifests

---

## License

This is a private repository. No public license is granted.
