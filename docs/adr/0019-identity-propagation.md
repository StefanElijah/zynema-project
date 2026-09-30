# ADR-0019: Identity propagation between services

- **Status:** Accepted
- **Date:** 2026-09-27
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 4 (domain services and resilience)

## Context

With the gateway authenticating every request (ADR-0014), services behind it
still need two things: the caller's **identity** and a way to **act on their
behalf** when they call another service. Three shapes were considered:

1. **Shared secret between services** (`X-Internal-Token: ...`). Simple, but any
   service that leaks the secret can impersonate every user, and the secret
   rotation story is a deployment of every service at once.
2. **Client-credentials tokens per service** — each service holds its own OAuth2
   client and mints tokens. Correct for machine-initiated work, but a request
   that started as a user ends up executed as a service account, losing the end
   user for downstream authorisation and audit.
3. **Token relay** — the incoming `Authorization` header travels with the
   outgoing call, so the downstream service sees the very same user and applies
   its own rules.

## Decision

Relay the incoming token (option 3), centrally, for user-initiated calls:

- A single `FeignRequestInterceptor` in `common` copies `Authorization` onto
  every outgoing request. It also propagates `X-Correlation-Id` and, when there
  is an active one, the W3C `traceparent`.
- **Identity is resolved from the token, never from a header.** Services ask
  `user-service` for `/users/me` through a Feign client (`CurrentAccountService`,
  `PlaybackDependencies`), keyed by the `sub` claim, and cache the answer in
  Redis for a few minutes. A service cannot be told who it is talking to.
- The `sub` claim is the **Keycloak subject**, not the local user id; the mapping
  to the domain user happens in `user-service`, which is also what provisions it
  just-in-time. Local ids never appear in a token.
- **Degradation is explicit:** when `user-service` is unavailable, the call
  fails with `503` and the client retries — the request cannot be authorised
  without an identity, so pretending otherwise would be worse.
- Machine-initiated calls (none yet) would use client credentials, which is why
  the interceptor relays the header only when one is present.

## Consequences

**Positive**

- One identity from edge to database: the same user is used for authorisation,
  ownership checks and audit, and a leaked internal secret cannot impersonate
  anybody because no such secret exists.
- Each service enforces its own rules with the real user, so a missing gateway
  rule is a second chance instead of a hole.
- The interceptor is the only place that knows how context is carried; every
  Feign client inherits it.

**Negative**

- A service needs `user-service` up to authorise a request, which couples
  availability. That is accepted: the identity lookup is cached, and an
  unavailable identity provider must fail closed.
- The token's lifetime is the request's lifetime — a token that expires mid-call
  chain fails downstream, which is the intended behaviour.
- Service-initiated workflows (notifications, scheduled jobs) will need the
  client-credentials path when they appear.

## Notes

- The interceptor and the mapper live in `common` and are registered by
  `CommonFeignAutoConfiguration`, so a new service gets the behaviour by adding
  the dependency.
- Tests assert both halves: the header arrives at the downstream stub, and the
  cache prevents a second call for the same user. The correlation id is
  checked to be forwarded unchanged, since it is what ties a request together
  across services in the logs.
