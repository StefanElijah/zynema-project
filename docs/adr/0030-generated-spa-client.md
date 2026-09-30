# ADR-0030: A generated SPA client for the BFF surface

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 8 (complete frontend)

## Context

By Fase 8 the BFF composes every screen the SPA shows (ADR-0021), it is
documented by springdoc, and every screen needs the same things from it:
typed request and response shapes, React Query hooks, the bearer token, the
401 renewal and the platform's error envelope.

Hand-writing that layer was the alternative. It had already failed once in
small: the player called `/web/catalog/{id}` through a hand-rolled axios call
with hand-rolled types, and nothing tied those types to the controller that
produced them. A rename in a DTO surfaces, at best, at runtime — the exact
failure mode the view-model rule of ADR-0021 exists to prevent, moved one
layer out.

## Decision

The typed client is **generated** with orval from the BFF's OpenAPI document:

- `libs/api-contracts/openapi.json` is a **committed snapshot** of the spec.
  `pnpm api:refresh` pulls a new one from a running BFF; `pnpm api:generate`
  turns the snapshot into code. Generation is deterministic and works offline.
- The output is one file of types, request functions and React Query hooks
  (`src/generated/zynema.ts`), committed on purpose: the generated diff **is**
  the contract change, reviewable like any other code.
- Every call goes through a hand-written **axios mutator** that delegates to a
  single `AXIOS_INSTANCE`. The app configures that instance once (base URL,
  token interceptor, single 401 renewal); generated code never knows about
  authentication. Error types are pinned to the platform envelope
  (`{ status, error, message }`).
- The SPA imports from a thin wrapper (`src/lib/api`), which re-exports the
  contracts and guarantees the instance is configured before any hook runs.
- `**/generated/` stays ignored in `.gitignore` **except** this package's
  generated client, where the exception is explicit.

Two consequences deserve to be written down:

1. **The contract is the spec, not the controller.** Anything a client must
   send has to be documentable: the `Idempotency-Key` header existed in the
   controller and enforced idempotency, but was invisible in the spec, so the
   generated client could not send it. It is now an annotated parameter.
2. **Hand-written transport survives where it has a product story.** The
   player's session lifecycle (`lib/api/playback.ts`) stays hand-written
   because its failure mapping — paywall, not-ready, stream limit,
   unavailable — is domain behaviour with unit tests, not a mechanical
   passthrough.

## Alternatives considered

- **Hand-written client** — rejected: drift between DTOs and types is silent,
  and the whole point of the generated layer is that a backend rename fails
  the frontend build.
- **Type-only generation** (`openapi-typescript`) — rejected: types without
  hooks leave the request layer hand-rolled, which is where the token and
  renewal logic would have to be duplicated.
- **Generating in CI only** — rejected: the diff stops being reviewable and a
  fresh clone cannot type-check offline.

## Consequences

- A backend contract change is a two-step ritual: `api:refresh`,
  `api:generate`, both pnpm scripts.
- Orval cannot invent what the spec omits: documenting endpoints is now a
  frontend concern, not a courtesy.
- The generated file is never edited by hand; local changes are lost on the
  next generation.
