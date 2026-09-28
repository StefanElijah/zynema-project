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

## **[F2] Service boots locally but fails with a YAML error in another profile**

Symptom: a service that used to start suddenly fails with
`mapping values are not allowed here` or starts with half its configuration
missing (no datasource, no redis, no security).

Cause: a YAML block was concatenated onto the previous line, e.g.
`minimum-idle: 2  data:` instead of `minimum-idle: 2` + a new line. The value
becomes the string `"2  data:"` and the following block is silently re-parented
under the wrong key.

Why it hides: a service with no context-load test never parses its own
`application.yml` during the build. `mvn compile` cannot catch it.

Fix: split the blocks, and add a Testcontainers context test to the service —
that test fails immediately on a malformed file.

## **[F2] Testcontainers: "Could not find a valid Docker environment"**

Symptom: every Testcontainers test fails with
`IllegalStateException: Could not find a valid Docker environment`, and the log
shows `BadRequestException (Status 400 ...)` for `/info`.

Cause: Docker Engine 29 raised the minimum API version to **1.44**, but
docker-java (bundled with Testcontainers < 1.21.4) defaults to 1.32. The
daemon rejects the handshake.

Fix: keep `testcontainers.version` at **1.21.4+** in `backend/pom.xml`.
Workaround for older versions: `src/test/resources/docker-java.properties`
with `api.version=1.44`.

## **[F2] `@Cacheable` does nothing (self-invocation)**

Symptom: a cached endpoint still hits the database every call; the cache
region stays empty.

Cause: the annotated method is called from another method **of the same bean**
(`listMovies()` calling `this.list()`). Spring AOP only applies caching through
the proxy, so internal calls bypass it.

Fix: annotate the public entry point that controllers actually call, or inject
the bean and call through the proxy.

## **[F2] Writes return stale data (cache read inside a write)**

Symptom: a `PUT` responds 200 but the body still shows the old values, and the
next `GET` is correct.

Cause: the write method calls a `@Cacheable` read to build its response. The
eviction runs **after** the method returns, so the read served the old cached
entry.

Fix: build the write response with a non-cached path (see
`CatalogQueryService.buildDetail`) and let `@CacheEvict` clear the regions.

## **[F2] 404 becomes 500, or validation errors become 500**

Symptom: an unknown URL returns 500 instead of 404; `?size=500` returns 500
instead of 400.

Cause: a catch-all `@ExceptionHandler(Exception.class)` in the shared
`GlobalExceptionHandler` intercepts framework exceptions that already carry a
status (`NoResourceFoundException`, `ConstraintViolationException`,
`MissingServletRequestParameterException`).

Fix: `common` handles them explicitly. Note that
`NoResourceFoundException` implements `ErrorResponse` but does **not** extend
`ErrorResponseException`, and it lives in `spring-webmvc` — which `common` does
not depend on. The catch-all therefore checks
`ex instanceof ErrorResponse` and reuses its status for 4xx.

## **[F3] Keycloak import fails: "Unrecognized field postLogoutRedirectUris"**

Symptom: the container starts and immediately exits with
`Failed to import realms` and `Unrecognized field "postLogoutRedirectUris"`.

Cause: in a realm export, post-logout redirect URIs are **not** a top-level
client field. The importer rejects the whole file.

Fix: put them in `attributes`:

```json
"attributes": {
  "pkce.code.challenge.method": "S256",
  "post.logout.redirect.uris": "http://localhost:5173/*"
}
```

## **[F3] "Port 8081 was already in use" when running user-service locally**

Symptom: user-service fails to start on the host; Keycloak is running.

Cause: Keycloak was published on host port 8081, which is user-service's port.

Fix: Keycloak now publishes **8180** (`KC_HOSTNAME=http://localhost:8180`).
Ports 8080–8087 belong to the services; keep Keycloak out of that range.

## **[F3] Every authenticated request returns 401 after changing Keycloak's URL**

Symptom: tokens are issued fine, the SPA logs in, but every API call answers
401 with `invalid_token`.

Cause: the `iss` claim no longer matches `zynema.security.issuer-uri` (usually
because Keycloak's `KC_HOSTNAME` or host port changed).

Fix: the public issuer must be identical in three places — `KC_HOSTNAME`,
`ZYNEMA_SECURITY_ISSUER_URI` and the SPA's `VITE_KEYCLOAK_URL`. The JWKS URI can
stay internal (ADR-0015). To confirm, decode the token and compare `iss`:

```bash
curl -s -X POST http://localhost:8180/realms/zynema/protocol/openid-connect/token \
  -d grant_type=password -d client_id=zynema-cli \
  -d client_secret=zynema-cli-dev-secret -d username=demo -d password=demo \
  | jq -r .access_token | cut -d. -f2 | base64 -d 2>/dev/null | jq .iss
```

## **[F3] A request to a stopped service returns 500 instead of 503**

Symptom: calling a route whose service is not registered returns
`"status": 500` with "Internal server error".

Cause: the catch-all exception handler collapsed the gateway's routing failure
(which already carries 503) into a generic 500.

Fix: both exception handlers now honour 4xx **and** 5xx statuses carried by
framework exceptions that implement `ErrorResponse`. A down dependency must look
like a down dependency.

## **[F3] Actuator and Swagger became protected after adding a custom security chain**

Symptom: Prometheus can no longer scrape `/actuator/prometheus`; Swagger UI
redirects to a login.

Cause: a service that declares its own `SecurityFilterChain` replaces the
default one, and the default one is what made those paths public.

Fix: every explicit chain starts with the shared lists:

```java
.requestMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
.requestMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()
```

## **[F3] Responses carry X-Correlation-Id twice**

Symptom: `curl -D -` shows two `X-Correlation-Id` headers.

Cause: the gateway sets it on the response and the downstream service echoes it
back; the gateway merges the downstream headers after its dedupe filter has
already run. Both values are identical because the gateway propagates the id in
the request. Repeated headers with the same value are valid HTTP and readers
take the first, so this is cosmetic.

## **[F4] Nothing from the Config Server reaches the services**

Symptom: `/actuator/env` shows no `configserver` property source, the shared
properties (tracing endpoint, health groups, security settings) have no effect,
and the Config Server logs no requests. Nothing fails: the app starts with its
local `application.yml` and _looks_ healthy.

Cause: `spring.config.import: optional:configserver:...` is **silently skipped**
when `spring-cloud-starter-config` is not on the classpath. `optional:` turns a
missing server into a non-event, so a missing _client_ is invisible too. This
went unnoticed for three phases.

Fix: the dependency must be declared — it is already added to all eight
config-consuming services:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

Verify after restarting: the service logs `Fetching config from server at ...`,
and `/actuator/env` lists a `configserver:.../application.yml` property source.
As a sanity check, the Config Server log should show one request per service at
startup. When in doubt about which properties are actually active, trust
`/actuator/env` over the repository files.

## **[F4] "Included health contributor 'X' in group 'readiness' does not exist"**

Symptom: a service fails to start with that `APPLICATION FAILED TO START`
report, right after the shared config starts being consumed.

Cause: the health group is defined once for every service, but Boot refuses to
start when a group names an indicator the service does not have. `db` exists in
the services with a database, not in `api-gateway` or `bff-service`.

Fix: keep in the shared `application.yml` only what **every** consumer has
(`redis`), and extend the group per service in `infra/config-repo/<name>.yml`:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: db,redis
```

The `api-gateway` and `bff-service` simply do not add the `db` file. Remember a
local `application.yml` cannot override the shared value: config-server
properties win, which is exactly why the exception lives in the repository.

## **[F5] Tempo answers with an empty trace list right after generating traffic**

Symptom: the services log normal activity, a request traces through the gateway,
and `GET /api/search` still returns `{"traces":[]}` — including for traces you
can see were created minutes ago.

Cause: Tempo's search API only serves **completed blocks**. The local config
(`infra/observability/tempo-config.yaml`) flushes every `max_block_duration: 5m`,
and until that flush the spans live in the ingester's WAL, unsearchable. An
empty result therefore says nothing about your services.

How to tell the two apart:

1. Wait for the next block (up to ~5 minutes) and query again; and/or
2. Prove the exporter is running independently of Tempo: restart the service
   with the endpoint pointed at a dead port and generate traffic:
   ```bash
   MANAGEMENT_OTLP_TRACING_ENDPOINT=http://localhost:9/v1/traces java -jar app.jar
   ```
   A working exporter logs `Failed to export spans ... Connection refused` within
   seconds. No log line means the spans are not being created at all — then look
   at the tracing dependencies and the sampled probability.

Do not chase "missing traces" in the application before ruling out the flush
window; it looks exactly like a broken exporter.

## **[F5] A BFF endpoint returns 503 "LoadBalancer does not contain an instance for the service localhost"**

Symptom: the BFF answers 503 blaming the load balancer for a service called
`localhost`.

Cause: a `@LoadBalanced WebClient.Builder` resolves **any** host as a service
id — including an absolute `http://host:port` URL, which it then looks up in
Eureka. The BFF builds its clients from a plain builder and attaches
`ReactorLoadBalancerExchangeFilterFunction` only when the configured base URL
starts with `lb://`, so production still resolves through Eureka while tests
point at a fixed WireMock URL.

## **[F5] `@CircuitBreaker`/`@Retry` do not seem to apply at all**

Symptom: a downstream 503 reaches the client raw instead of degrading or
opening the breaker; nothing appears in `/actuator/circuitbreakers`.

Cause: the Resilience4j annotations are implemented as aspects. Without
`spring-boot-starter-aop` (plus `resilience4j-reactor` for reactive return
types) they are **silently ignored** — the pom looks configured, the code
compiles, and no behaviour changes.

Fix: declare both dependencies. Then check the instance exists:
`curl localhost:8086/actuator/circuitbreakers`.

## **[F5] Catalogue writes are projected with the previous values**

Symptom: right after an admin `POST`/`PUT`, the read model (and therefore the
API) still shows the old title or genres.

Cause: the projector reads the write tables through JDBC, which does not see
un-flushed JPA state. `contentRepository.save(...)` may keep the change in the
persistence context.

Fix: `saveAndFlush(...)` before projecting (the command service does). The same
applies to any future writer: flush first, then project.

## **[F5] The read model is empty after migrating**

Symptom: every catalogue list is empty right after applying
`V4__catalog_read_model.sql`, even though the write tables have rows.

Cause: the migration creates the projection empty by design.

Fix: restart catalog-service. `CatalogReadModelBackfill` detects an empty
projection at startup and rebuilds it from the write tables (one log line:
"Read model was empty: projected N catalogue titles"). A full rebuild can also
be triggered by truncating `content_read_model` and restarting.

## **[F4] Tempo answers 404 on the OTLP endpoint**

Symptom: spans never arrive and the exporter logs `404 Not Found`.

Cause: with `http/protobuf`, Micrometer appends only the signal path if the
endpoint already ends in `/v1/traces`; pointing it at `http://tempo:4318`
leaves the request at the root, which Tempo does not serve.

Fix: use the full signal path, `http://tempo:4318/v1/traces`. Docker and local
runs differ only in the host, and the property is a literal in the config repo
(a placeholder cannot be resolved by the OTLP autoconfiguration at startup).

## **[F4] The circuit breaker never opens on a Feign client**

Symptom: the resilience4j instance for a downstream call stays closed while the
dependency is down, or the instance registered in `/actuator/circuitbreakers`
has an unexpected name.

Causes, both of which bit us:

- With the OpenFeign integration enabled, the annotation-less client gets an
  instance named after the generated delegate (`UserServiceClientcurrentUser`),
  which is not the name you configure. We disable the integration
  (`spring.cloud.openfeign.circuitbreaker.enabled: false`) and annotate the calls
  explicitly with `@CircuitBreaker(name = "user-service")`, `@Retry` and
  `@Bulkhead`, so the instance name is a decision rather than a generated
  string. Do not draw conclusions from a single call: the breaker needs
  `minimumNumberOfCalls` failures before it is observable.
- Adding `contextId` to `@FeignClient` changes the configuration key and can
  silently disconnect the client from its custom configuration. Remove it and
  keep one client per target.

## **[F4] Hibernate validations fail on a column that "looks" right**

Symptom: `Schema-validation: wrong column type ... found [char], expecting
[varchar]` on startup, usually for a three-letter column such as `currency` or
`region`.

Cause: `CHAR(3)` and `VARCHAR(3)` are different types to Hibernate's validator,
and the entity was annotated `length = 3`, which maps to `varchar`.

Fix: either change the migration to `VARCHAR(3)` or annotate the field
(`columnDefinition = "char(3)"`). Prefer the migration: PostgreSQL pads `CHAR`
with spaces, and a padded `"MXN"` breaks equality comparisons. Also note the
migration edit is not picked up by an incremental build — recompile with
`clean`.

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

1. The Vite dev server proxies `/api` to `http://localhost:8080` (the
   gateway), which forwards `/api/v1/**` to the services and
   `/api/v1/web/**` to the BFF. Make sure both are running:
   ```bash
   docker compose ps api-gateway bff-service
   ```
2. Check the gateway is forwarding correctly:
   ```bash
   curl http://localhost:8080/actuator/health
   curl http://localhost:8080/api/v1/catalog/movies        # public read
   curl http://localhost:8080/api/v1/web/home             # public BFF screen
   curl http://localhost:8080/api/v1/web/account          # 401 without a token
   ```
3. A 401 on `/api/v1/web/account` or `/api/v1/web/profiles/**` is expected
   anonymously: those are personal views and need a Bearer token. The landing
   page and content metadata are public on purpose.

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
