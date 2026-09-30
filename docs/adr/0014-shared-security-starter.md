# ADR-0014: Shared security starter in `zynema-common`

- **Status:** Accepted
- **Date:** 2026-09-26
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 3 (authentication)

## Context

Fase 3 turns eight services into resource servers. Every one of them needs the
same four things:

1. A JWT decoder that validates the issuer and the audience and fetches keys.
2. A converter that turns Keycloak realm roles into Spring authorities.
3. A 401 and a 403 handler that answer with the platform's `ApiError` envelope
   rather than an HTML error page or an empty body.
4. A baseline posture: stateless, no form login, no CSRF.

Only a few services have rules that differ from the rest (`catalog` publishes
part of its API anonymously, `user` splits self-service from admin, `auth`
exposes a public config endpoint, the `gateway` decides per route). Those rules
are five lines each; the shared pieces above are hundreds.

## Decision

The shared pieces live in `zynema-common`, auto-configured for both stacks
(`CommonSecurityAutoConfiguration` + `CommonReactiveSecurityAutoConfiguration`),
exactly like the exception handlers and correlation filters that already live
there. Concretely:

- `ZynemaSecurityProperties` (`zynema.security.*`).
- `JwtDecoder` / `ReactiveJwtDecoder` built from issuer + JWKS URI.
- `KeycloakRealmRoleConverter`.
- `ApiAuthenticationEntryPoint` / `ApiAccessDeniedHandler` and their reactive
  counterparts.
- `ServletSecuritySupport` / `ReactiveSecuritySupport`: the shared posture as a
  single `apply(http)` call.
- A **default filter chain** (`@ConditionalOnMissingBean`) that requires
  authentication for everything except operational endpoints and API docs.

A service with special rules declares its own `SecurityFilterChain` and calls
`support.apply(http)`; the default chain backs off. A service without special
rules needs no security code at all.

## Consequences

**Positive**

- **Secure by default.** A new service is authenticated as soon as it depends on
  `common` and the resource-server starter. Forgetting to write a security
  config no longer means shipping an open service.
- One place to fix a security defect: the audience check, the role mapping and
  the error envelope cannot drift between services.
- The 401/403 contract is identical everywhere, which the frontend and the BFF
  can rely on.

**Negative**

- Security for a given service is not visible in that service's source. This is
  mitigated by explicit names, by the ADR and the runbook, and by every service
  that _does_ have rules showing them in its own `SecurityConfig`.
- `common` now compiles against Spring Security. The dependencies are
  `<optional>`, guarded by `@ConditionalOnClass`, and services that need them
  already declare the starter, so nothing leaks transitively.

## Notes

- `common` has no dependency on `spring-webmvc`; the catch-all handler
  recognises framework failures through the `ErrorResponse` interface, which
  lives in `spring-web` and exists in both stacks.
- The default chains are tested: `DefaultSecurityChainTest` (servlet) proves
  that a service with no configuration rejects anonymous calls with a JSON 401
  and keeps `/actuator/health` public.
