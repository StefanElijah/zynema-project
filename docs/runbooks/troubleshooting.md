# Troubleshooting

Quick answers to common problems.

## "Service X won't start in Docker"

1. Check the logs: `docker compose logs -f <service>`.
2. Check the health: `docker compose ps` — look for `Restarting`.
3. Check the dependencies: most services depend on `eureka-server` and
   `config-server` being `healthy`. If config-server is down, everything
   else is.
4. The most common root cause: the config repo at `infra/config-repo`
   was not committed. Re-init:
   ```bash
   cd infra/config-repo
   git status
   # If empty:
   git add . && git commit -m "config: initial"
   cd ../..
   docker compose restart config-server
   ```

## "Frontend shows 'Network Error' on /api calls"

1. The Vite dev server proxies `/api` to `http://localhost:8086` (the
   BFF). Make sure the BFF is running:
   ```bash
   docker compose ps bff-service
   ```
2. Check the gateway is forwarding correctly:
   ```bash
   curl http://localhost:8080/actuator/health
   curl http://localhost:8080/api/web/catalog  # should 200 or 401
   ```
3. If you get a 401, you need to be logged in (Keycloak). The BFF
   expects a Bearer token.

## "Keycloak says 'Invalid username or password'"

- Default user is `admin` / `admin` (from `KEYCLOAK_ADMIN_PASSWORD`).
- Wait for Keycloak to be fully started: `docker compose logs -f keycloak`
  — look for `started in ...`.
- The first start takes 30-60s.

## "Tests fail with 'connection refused' to PostgreSQL"

- Testcontainers is supposed to spin up its own PostgreSQL. Make sure
  Docker is running.
- On Windows with WSL2 backend, this is usually fine. On Toolbox/Hyper-V,
  Testcontainers can be flaky.

## "Kafka topics aren't created"

- We have `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` in the compose, so
  topics are auto-created on first publish.
- If you want to create them explicitly, see
  `infra/scripts/kafka-topics.sh` (TODO).

## "Eureka shows services but they can't talk to each other"

- Check the `application-docker.yml` of each service: the
  `eureka.instance.hostname` should match the service name in
  `docker-compose.yml`.
- The services in compose use `SPRING_PROFILES_ACTIVE=docker`. If you
  run a service outside Docker, drop the profile.

## "docker compose config shows a YAML error"

- Run `docker compose config` to see where the error is.
- Most common: tabs vs spaces. YAML hates tabs.

## "I need to wipe everything and start fresh"

```bash
make down    # docker compose down
make reset   # docker compose down -v (also removes volumes)
make up-core
```

## Getting more help

- `docs/architecture/` for the big picture.
- `docs/adr/` for why a piece of the stack is the way it is.
- `docs/learning/` for deep dives on the patterns (some are TODO).
- `docs/runbooks/disaster-recovery.md` (TODO) for the "oh no" scenarios.
