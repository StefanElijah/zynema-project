# ADR-0015: Split-horizon Keycloak issuer and audience validation

- **Status:** Accepted
- **Date:** 2026-09-26
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 3 (authentication)

## Context

Keycloak is reached from two places in this project:

- the **browser**, through `http://localhost:8180` (the host port), which starts
  the login flow and receives tokens;
- the **services**, which run either on the host or inside the Docker network,
  where the same Keycloak answers as `http://keycloak:8080`.

A JWT carries an `iss` claim naming the host that issued it. Only one value can
win, and both sides must agree on it:

- If tokens carry `http://keycloak:8080/...`, the browser cannot validate them
  and the SPA's OIDC library rejects the login.
- If tokens carry `http://localhost:8180/...`, the browser is happy, but a
  service inside Docker that tries OIDC discovery against that issuer resolves
  `localhost` to its own container and fails.

Spring Boot's default resource-server setup makes this worse by using
`issuer-uri` for both purposes: discovery (network call at startup) and
validation.

## Decision

Set `KC_HOSTNAME` to the **public** URL so every token carries the same issuer
the browser uses, and separate the two concerns in the decoder:

- `zynema.security.issuer-uri` — the public issuer. Used to validate the `iss`
  claim and to fetch the frontend-facing endpoints.
- `zynema.security.jwk-set-uri` — where the service fetches signing keys. In
  Docker this is `http://keycloak:8080/.../certs`, which is not resolvable from
  the browser and does not need to be.
- `zynema.security.audience` — the `aud` value the platform requires.

The decoder is built with `NimbusJwtDecoder.withJwkSetUri(...)` plus an explicit
validator chain (`iss` + `aud`), instead of `fromIssuerLocation(...)`. Keys are
fetched lazily on the first token, so **startup performs no network call**:
services still boot in the `core` profile with Keycloak stopped.

The realm also gains an audience mapper so `aud` contains `zynema-api`.

## Consequences

**Positive**

- One issuer value works everywhere: browser, host services and containers.
- Token validation is stricter than Spring's default: without the audience
  check, a token minted for _any_ client of the same realm would be accepted by
  every service.
- The `core` profile stays bootable without Keycloak, which the previous phases
  had already established and this phase could easily have broken.

**Negative**

- The issuer and the key location must be kept consistent manually; a wrong
  combination fails at the first authenticated request rather than at startup.
  The failure mode is explicit (401 with `invalid_token`), and the runbook
  documents it.
- Tests that need real tokens must configure both properties from the container
  (see `KeycloakTokenAuthenticationTests`).

## Notes

- Keycloak's host port is **8180** on purpose: 8080–8087 belong to the services,
  and 8081 collided with user-service.
- Local development without Docker resolves both properties to the same URL, so
  the split only exists where it matters.
