# Spring Cloud Config — local source

This directory holds the **shared config files** served by Spring Cloud
Config Server at runtime. Files here are tracked in the **main project
repository** (not as a submodule) to keep the dev experience simple.

## Why not a separate Git repo?

We evaluated the standard pattern of having `infra/config-repo` be its own
Git repo (so config-server can watch for commits and trigger refresh).
We rejected it for Fase 0 because:

- Adds submodule complexity to the monorepo.
- Adds "where do I commit my config change?" friction.
- The dev value of Git-backed refresh doesn't outweigh the operational cost.

**Trade-off accepted:** we don't get automatic refresh on config change in
dev. Restart the config-server container to pick up edits:
`docker compose restart config-server`.

If/when we need auto-refresh in production, we move this directory to its
own Git repo and point the config-server at it.

## Structure

```
config-repo/
├── README.md
├── application.yml             # Shared defaults for ALL consumers
├── auth-service.yml            # Per-service overrides
├── catalog-service.yml         #   (each service adds `db` to the
├── user-service.yml            #    readiness group; gateway and BFF
├── payment-service.yml         #    only have Redis and do not add it)
├── playback-service.yml
└── notification-service.yml
```

`eureka-server` and `config-server` do not consume this repository: they are the
bootstrap layer, so a config client in them would be a chicken-and-egg problem.
`api-gateway` and `bff-service` consume it but need no per-service file.

## Who consumes it, and since when

Services need **`spring-cloud-starter-config`** on the classpath; without it
Spring silently skips an `optional:configserver:` import (see the F4 runbook
entry). The file is only half of the contract — the dependency is the other.

## How it's used

The `config-server` container mounts this directory:

```yaml
volumes:
  - ./infra/config-repo:/config-repo
environment:
  SPRING_CLOUD_CONFIG_SERVER_GIT_URI: file:///config-repo
```

## Adding a new service

1. Add `spring-cloud-starter-config` to the service's `pom.xml`.
2. If it has a database, create `<service-name>.yml` extending the readiness
   group (see any existing file); otherwise there is nothing to add.
3. Commit in the main repo: `git add infra/config-repo/ && git commit -m "config: add <service-name>"`.
4. Restart config-server: `docker compose restart config-server`.
5. Verify the fetch: the service logs `Fetching config from server at ...` and
   `/actuator/env` shows a `configserver` property source.
