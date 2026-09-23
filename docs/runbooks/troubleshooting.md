# Troubleshooting

Quick answers to common problems. Sections marked **[F1]** were discovered
while building Fase 1 (skeleton) — they are the traps we already hit.

## **[F1] Config Server hangs and never answers HTTP requests**

Symptom: `curl http://localhost:8888/application/default` times out; the
config-server registers in Eureka but never responds; `jstack` shows
`http-nio-8888-exec-*` threads blocked in `ConfigServerConfigDataLoader`.

Cause: the config **served** by the config server contained
`spring.config.import: configserver:...`. When serving a request, the native
repository processed that import and tried to fetch config from itself →
self-referential loop.

Rule: `infra/config-repo/application.yml` must NEVER contain
`spring.config.import`. That property belongs in each service's **local**
`application.yml` only.

## **[F1] Config Server overrides local application.yml values**

Symptom: a service loses a property it defines locally (e.g. the gateway's
`management.endpoints.web.exposure.include` list).

Cause: by design, properties from the config server have **higher precedence**
than the service's own `application.yml`.

Rule: put in `infra/config-repo/application.yml` only properties that are
truly identical across ALL services. Anything service-specific (endpoint
exposure lists, `spring.application.name`, ports, …) stays local.

## **[F1] /actuator/gateway 404 on the API gateway**

Symptom: `/actuator/gateway/routes` returns 404 even with
`management.endpoints.web.exposure.include=gateway`.

Cause: Spring Cloud Gateway 4.3.x disables the gateway actuator endpoint's
**access** by default.

Fix: in `api-gateway/src/main/resources/application.yml`:
```yaml
management:
  endpoint:
    gateway:
      access: read-only
```

## **[F1] "Spring Boot [3.5.0] is not compatible with this Spring Cloud release train"**

Symptom: context load fails with `CompatibilityNotMetException`.

Cause: Spring Cloud 2024.0.x targets Boot 3.4.x. We use Boot 3.5.x.

Fix: Spring Cloud **2025.0.x** (see ADR-0011). Note the breaking renames in
2025.0: `spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux`
and `spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*`.

## **[F1] springdoc + WebFlux: "No more pattern data allowed after ** pattern element"**

Symptom: WebFlux app fails to start inside
`SwaggerWebFluxConfigurer.addResourceHandlers`.

Cause: regression introduced in springdoc 2.8.15 (path pattern combination
with Spring Framework 6.2).

Fix: pin `springdoc.version` to **2.8.14** in `backend/pom.xml`.

## **[F1] Docker build fails: "mvn: not found" or "Child module does not exist"**

Symptom: `docker compose build <service>` fails with exit 127 or
`Child module ... does not exist`.

Cause:
- `eclipse-temurin:*-jdk-alpine` has no Maven.
- The parent POM lists all 12 modules, but the Dockerfile only copied a few
  module POMs.

Fix (already applied to all Dockerfiles): build stage uses
`maven:3.9.9-eclipse-temurin-21-alpine`, copies the whole `backend/`
directory, and caches `~/.m2` with a BuildKit cache mount:
```dockerfile
# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY backend/ backend/
RUN --mount=type=cache,target=/root/.m2 \
    cd backend && mvn -B -pl <service> -am -DskipTests clean package
```

## **[F1] "illegal character: '\ufeff'" when compiling Java**

Symptom: `mvn verify` fails with `illegal character: '\ufeff'` on line 1.

Cause: a Java source was saved with a UTF-8 BOM.

Fix: rewrite the file without BOM (e.g. `[System.IO.File]::WriteAllText` on
Windows, or `sed -i '1s/^\xEF\xBB\xBF//' file`).

## **[F1] YAML parse error: "mapping values are not allowed here"**

Symptom: service fails to start, `SnakeYAML` reports the error with a line
and column.

Cause: an unquoted YAML value containing `": "` — e.g.
`description: Backend-for-Frontend: WebClient aggregator...`.

Fix: quote the value: `description: "Backend-for-Frontend: WebClient aggregator..."`.

## "Service X won't start in Docker"

1. Check the logs: `docker compose logs -f <service>`.
2. Check the health: `docker compose ps` — look for `Restarting`.
3. Check the dependencies: most services depend on `eureka-server` and
   `config-server` being `healthy`. If config-server is down, everything
   else is.
4. Remember `infra/config-repo` is a plain directory tracked in the main
   repo (not a nested git repo). Restart the config server after editing it:
   ```bash
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
