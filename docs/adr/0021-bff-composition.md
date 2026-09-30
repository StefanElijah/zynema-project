# ADR-0021: The BFF composes screens, it does not enforce business rules

- **Status:** Accepted
- **Date:** 2026-09-28
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 5 (BFF reactive and CQRS)

## Context

The frontend needs screens, not services. The landing page wants a hero and
rails from the catalogue; the account page wants an identity, a local profile
and a plan; a profile's home wants two activity rails joined with artwork and
titles that live somewhere else. Done from the browser, that is several round
trips and the same orchestration logic reimplemented in every client.

The platform already has: WebFlux and a gateway at the edge (ADR-0005 chose
WebClient for the BFF), services that validate the user's token themselves
(ADR-0014, ADR-0019), and a catalogue that will expose a read model
(ADR-0006). What was missing was the layer that turns five services into one
screen — and the rules for what that layer may decide.

## Decision

`bff-service` exposes one endpoint per screen under `/api/v1/web/**`, and
nothing else:

| Endpoint                         | Access | Composes                                                  |
| -------------------------------- | ------ | --------------------------------------------------------- |
| `GET /home`                      | public | hero + three rails, catalogue only                        |
| `GET /catalog/{idOrSlug}`        | public | detail + playback availability + resume position          |
| `GET /account`                   | token  | token claims + user-service + subscription + entitlements |
| `GET /profiles/{profileId}/home` | token  | continue-watching + my list, joined with the catalogue    |

Five rules make it predictable:

1. **The view model is the contract.** The BFF defines its own records and
   reads JSON from the services; it does not import their classes. A field
   renamed in a service breaks a test here instead of the frontend silently.
2. **The BFF never decides access.** It reports what it was told: entitlements
   come from payment-service, ownership of a profile is enforced by
   user-service, and the paywall is enforced by playback-service. The BFF's own
   security chain only separates public from personal screens.
3. **Degradation is explicit and per section.** A required dependency that
   fails is a `503` — a screen without content is not a screen. An optional
   section (plan, resume position, a rail) is omitted and named in
   `degraded: ["PLAYBACK", "MY_LIST", ...]`, so the UI can say "temporarily
   unavailable" instead of showing an empty lie. "No plan" is **not** degraded:
   it is an answer, and it renders a paywall.
4. **Watching requires an account and an active plan.** Browsing (home and
   metadata) is anonymous, because the catalogue is the shop window. Starting
   playback checks `entitlements.active` in playback-service: no plan is
   `402 SUBSCRIPTION_REQUIRED` (the frontend shows the paywall), an unverifiable
   plan is `503` (retry) — the platform never guesses "free".
5. **Aggregated screens are cached whole, with TTLs instead of eviction.** The
   landing page is one shared entry (5 min); detail payloads are cached by id
   (2 min) because they do not depend on the caller; personal views are keyed by
   subject and profile (60 s). Writes happen in the domain services, which know
   nothing about their consumers, so bounded staleness is the honest contract.
   The cache aspect is ordered outside the resilience aspects, so a cached
   screen survives the dependency's circuit being open.

Resilience is per dependency: WebClient timeouts (1 s connect, 3 s response),
`@Retry( @CircuitBreaker( @Bulkhead( ) ) )` with instance names matching the
service (`catalog-service`, `user-service`, `payment-service`), and a
classification that lives in code because YAML cannot express it: **4xx is not a
failure** (a 404 subscriber must not open the breaker for everybody) and only
transport errors and 5xx are retried.

## Consequences

**Positive**

- One round trip per screen, with a contract owned by this service and
  documented in its OpenAPI (the source of the SPA's generated client in
  Fase 8).
- Failures are scoped: losing payment degrades one section of one screen;
  losing the catalogue fails the screen that cannot exist without it.
- The BFF holds no business rule that could drift from the service that owns it.

**Negative**

- A degraded section means the frontend must understand `degraded`; an
  alternative (silently empty rails) would be simpler and dishonest.
- Aggregation multiplies load: one landing page is five catalogue calls. They
  are cached and bounded by the bulkhead, and the read model (ADR-0022) is the
  next step if it stops being enough.
- Two ways to reach the catalogue (directly and through the BFF) must be
  reflected in the gateway's authorization rules; the tests in both services
  pin the same matrix.

## Notes

- Three traps worth remembering, all found by tests in this phase:
  `@CircuitBreaker`/`@Retry` are aspect-based and are **silently ignored**
  without `spring-boot-starter-aop`; a `@LoadBalanced` WebClient builder treats
  _any_ host as a service id and rejects `http://localhost:port` test URLs, so
  the load-balancer filter is attached only for `lb://` base URLs; and a
  generic `ParameterizedTypeReference<List<T>>` erases `T` and yields a list of
  `LinkedHashMap`, so parameterised responses are declared explicitly.
- The BFF cache uses the synchronous `RedisCacheManager`: Spring caches the
  value a reactive method emits, but the cache access itself is a short
  blocking round trip. Accepted for this scale, documented in
  `BffCacheConfig`; the escape hatch is a bounded-elastic offload.
- The gateway's rate limiter keys limits by **route id**; an entry for a route
  that did not exist (`catalog-admin`) was dead configuration and was replaced
  by the BFF route's own limit (`bff-web`).
