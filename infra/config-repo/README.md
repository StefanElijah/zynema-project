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
├── application.yml           # Shared defaults for ALL services
├── application-dev.yml       # Profile: dev
├── application-docker.yml    # Profile: docker (compose)
├── eureka-server.yml
├── config-server.yml
├── api-gateway.yml
├── auth-service.yml
├── user-service.yml
├── catalog-service.yml
├── payment-service.yml
├── playback-service.yml
├── bff-service.yml
└── notification-service.yml
```

## How it's used

The `config-server` container mounts this directory:

```yaml
volumes:
  - ./infra/config-repo:/config-repo
environment:
  SPRING_CLOUD_CONFIG_SERVER_GIT_URI: file:///config-repo
```

## Adding a new service

1. Create `<service-name>.yml` with overrides.
2. Commit in the main repo: `git add infra/config-repo/ && git commit -m "config: add <service-name>"`.
3. Restart config-server: `docker compose restart config-server`.
