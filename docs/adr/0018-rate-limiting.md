# ADR-0018: Rate limiting at the edge with a Redis token bucket

- **Status:** Accepted
- **Date:** 2026-09-27
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 4 (domain services and resilience)

## Context

The API has anonymous, expensive read endpoints (the public catalogue) and
authenticated write endpoints. Without a limit, one client can consume the
whole service and there is no signal that it is happening. Two shapes are
possible:

1. **Spring Cloud Gateway's stock `RequestRateLimiter`** — a Redis token bucket
   implemented with an atomic Lua script, configured per route in the route
   definition.
2. **Bucket4j** — a token bucket library with richer policies (per-plan quotas,
   refill schedules) that you wire yourself.

## Decision

Use the stock **`RedisRateLimiter`** (option 1), because it already is a
distributed token bucket and needs no new dependency, but **do not attach the
stock filter**. Its response to a denied request is a bare `429` with no body,
which would break the error contract every other endpoint follows.

Instead, a small `ApiRateLimitFilter`:

- resolves the identity: the authenticated subject when there is a token, the
  client address otherwise (first `X-Forwarded-For` entry behind a proxy). Keying
  anonymous traffic by address is what protects the public endpoints;
- consults the limiter and, when denied, answers **429 with the `ApiError`
  envelope plus `Retry-After`**, derived from the route's replenish rate;
- leaves routes without a configured limit untouched.

Limits live in `zynema.rate-limit.routes.*` (route id → replenish rate, burst,
requested tokens), so tuning does not require a code change. The limiter is
shared by every gateway instance through Redis, so the limit is global.

## Consequences

**Positive**

- One algorithm, no new dependency, atomic across instances.
- The contract stays uniform: clients parse 429 exactly like every other error.
- Per-route limits approximate tiers without extra machinery: anonymous
  catalogue reads are limited most aggressively, admin writes least.
- `X-RateLimit-*` headers are returned, so a well-behaved client can back off.

**Negative**

- Per-_plan_ limits (free vs premium) are not expressible: the bucket is chosen
  by route, not by the caller's subscription. Bucket4j remains the escape hatch
  if product needs that.
- The limiter is per-route and per-caller, not per-endpoint: a tight limit on a
  route affects every path under it.

## Notes

- Verified by `GatewayRateLimitTests` against a real Redis. The tests fire
  requests **concurrently** on purpose: a sequential test is a race against how
  fast the machine answers, because the bucket refills with time.
- `X-Correlation-Id`, `Retry-After` and the envelope are asserted, as is the
  fact that an unconfigured route is never throttled.
