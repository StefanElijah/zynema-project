# ADR-0002: Keycloak como IdP desde el día 1

- **Status:** Accepted — implemented in Fase 3
- **Date:** 2026-07-09
- **Deciders:** Project owner
- **Implemented by:** [ADR-0014](0014-shared-security-starter.md) (shared
  security starter), [ADR-0015](0015-split-horizon-keycloak.md) (issuer and
  audience handling), [ADR-0016](0016-spa-token-storage.md) (SPA tokens)

## Context

Authentication in a microservice system is the most expensive thing to
retrofit. We've all seen projects that started with a custom `auth-service`
using JWTs signed with a static key, then had to migrate to a real IdP
(Keycloak, Auth0, Cognito) once the project grew.

The pain of that migration is significant:

- Token claims change.
- The way you propagate identity through services changes.
- Tests need to be rewritten.

## Decision

We use **Keycloak as the IdP from day 1**. The realm is exported to
`infra/keycloak/realm-export/zynema-realm.json` and imported on container
start. The Spring Security config in every resource server validates
JWTs against the Keycloak public keys.

## Rationale

- Keycloak is open source, self-hostable, and well-supported.
- It speaks standard OIDC, so we can swap IdPs later without changing
  client code.
- It supports fine-grained roles, which we need for content-manager vs
  end-user separation.

## Consequences

- Every service that needs auth becomes a Spring Security resource server
  with `spring-boot-starter-oauth2-resource-server`.
- The frontend uses OIDC code flow with PKCE.
- Tests need a Keycloak container (Testcontainers) for any auth-requiring
  test.
- One more moving piece in dev: but `docker compose --profile auth up -d`
  brings it up.
