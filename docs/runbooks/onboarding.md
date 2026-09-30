# Onboarding — Zynema

Goal: a new developer (or future-you) can go from `git clone` to a running
stack in under 30 minutes.

## Prerequisites

| Tool           | Version       | Why                          |
| -------------- | ------------- | ---------------------------- |
| Docker Desktop | 4.x with WSL2 | All backend services + infra |
| pnpm           | 9.15.x        | Frontend deps                |
| Node           | 20 LTS        | Vite, scripts                |
| Java (JDK)     | 21 (LTS)      | Spring Boot                  |
| Maven          | 3.9.x         | Multi-module build           |
| Git            | 2.40+         | Source control               |

> Windows: use PowerShell 7+ (pre-installed on Windows 11). On macOS/Linux,
> any shell works.

## Steps

### 1. Clone and prepare

```bash
git clone https://github.com/<your-org>/zynema-project.git
cd zynema-project
cp .env.example .env
```

### 2. Start the core stack

```bash
make up-core
# or: docker compose --profile core up -d
```

This brings up: postgres, redis, kafka, eureka, config-server,
api-gateway, and the first domain services.

### 3. (Optional) Add auth, storage, observability

```bash
make up-auth            # keycloak + mailhog
make up-storage         # minio + nginx-hls
make up-observability   # prometheus, grafana, loki, tempo, promtail
```

### 4. Run the frontend

```bash
pnpm install
pnpm --filter zynema-frontend dev
```

Open <http://localhost:5173>.

### 5. Verify

| Service     | URL                                     | What to check                          |
| ----------- | --------------------------------------- | -------------------------------------- |
| Eureka      | <http://localhost:8761>                 | All services registered                |
| API Gateway | <http://localhost:8080/actuator/health> | `UP`                                   |
| Frontend    | <http://localhost:5173>                 | Zynema logo, home renders              |
| Keycloak    | <http://localhost:8180>                 | Login with `admin` / your env password |
| Grafana     | <http://localhost:3000>                 | Datasources loaded                     |

## Common pitfalls

- **Port already in use** (5432, 8761, 8080, 8081): change `.env` and
  `docker-compose.override.yml`.
- **`pnpm install` fails with `EACCES`**: never run as root, never run
  with `sudo`. Reinstall pnpm if needed.
- **Eureka shows no services**: check `docker compose logs eureka-server`
  and `docker compose logs config-server`. The config server is the
  most common failure point on first start.
- **Frontend shows blank page**: open the browser console. Most likely
  the API gateway isn't reachable. Check `docker compose ps`.

## What's next?

- Read `docs/architecture/01-context.md` to understand the big picture.
- Read `docs/architecture/02-containers.md` to see the components.
- Skim `docs/adr/` to understand the decisions behind the stack.
- Pick a `Fase N` and start implementing.
