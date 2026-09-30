# ADR-0011: Spring Cloud 2025.0.x (not 2024.0.x) with Spring Boot 3.5

- **Status:** Accepted
- **Date:** 2026-09-23
- **Deciders:** Project owner, AI co-pilot

## Context

The original plan paired **Spring Boot 3.5.x** with **Spring Cloud 2024.0.x**.
During Fase 1 (skeleton), the very first context-load test failed:

```
CompatibilityNotMetException: Spring Boot [3.5.0] is not compatible with
this Spring Cloud release train.
Action: Change Spring Boot version to one of the following versions [3.4.x]
```

The Spring Cloud compatibility verifier refuses that combination, and it is
right: Spring Cloud 2024.0.x is the release train for Spring Boot 3.4.x.

Options:

1. **Downgrade Boot to 3.4.x** — contradicts the confirmed decision to use
   Boot 3.5.x (latest stable LTS-adjacent line).
2. **Disable the verifier** (`spring.cloud.compatibility-verifier.enabled=false`)
   — hides a real incompatibility instead of solving it. Rejected.
3. **Upgrade to the release train that targets Boot 3.5.x**: Spring Cloud
   **2025.0.x**.

## Decision

We use **Spring Cloud 2025.0.3** with **Spring Boot 3.5.0**.

## Consequences

Spring Cloud 2025.0 renamed several artifacts and property prefixes:

| Old (2024.0.x)                 | New (2025.0.x)                                |
| ------------------------------ | --------------------------------------------- |
| `spring-cloud-starter-gateway` | `spring-cloud-starter-gateway-server-webflux` |
| `spring.cloud.gateway.*`       | `spring.cloud.gateway.server.webflux.*`       |

Both are applied in this repo:

- `backend/api-gateway/pom.xml` uses the new starter artifact.
- `backend/api-gateway/src/main/resources/application.yml` uses the new
  `spring.cloud.gateway.server.webflux.*` prefix.
- `docker-compose.yml` sets the gateway discovery-locator env vars with the
  new prefix.

Also, Spring Cloud Gateway 4.3.x changed the actuator endpoint default:

- `/actuator/gateway` has its **access disabled by default**. We enable
  read-only access explicitly:
  `management.endpoint.gateway.access=read-only`.

## Notes

- springdoc is pinned to **2.8.14** because 2.8.15 introduced a path-pattern
  regression with Spring Framework 6.2 (see troubleshooting runbook).
- When upgrading Spring Cloud again, always re-run `mvn verify` — the
  compatibility verifier catches mismatches at test time, not at build time.
