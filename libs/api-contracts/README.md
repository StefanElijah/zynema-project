# @zynema/api-contracts

The SPA's typed API surface, generated from the BFF's OpenAPI document with
[orval](https://orval.dev). The frontend (and only the frontend) consumes it:
the BFF is the single entry point for the SPA (ADR-0004), so one generated
client covers every screen.

## Layout

```
libs/api-contracts/
├── openapi.json                  # committed snapshot of the BFF spec
├── orval.config.ts               # generator config (react-query + axios)
└── src/
    ├── mutator.ts                # the axios instance every call goes through
    ├── generated/zynema.ts       # types, request functions and hooks
    └── index.ts                  # public entry point
```

## Regenerating

```bash
# 1. refresh the snapshot from a running BFF (docker compose or a local jar)
pnpm --filter @zynema/api-contracts api:refresh

# 2. turn the snapshot into the client
pnpm --filter @zynema/api-contracts api:generate
```

`api:generate` works offline (it reads the committed `openapi.json`), so a
checkout without a backend can still type-check and build. Bump the snapshot
only after the BFF is running the new code: the generated diff is the contract
change, reviewable in the PR.

## Conventions

- **Hooks are generated, transport is ours.** The generated hooks call
  `customInstance`, which delegates to `AXIOS_INSTANCE`; the app configures
  that instance once (base URL, bearer token, 401 renewal) in
  `frontend/src/lib/api/client.ts`.
- **Errors are the platform envelope** (`status`, `error`, `message`), exposed
  as `ErrorType<T>` so UI code reads `error.response?.data?.message` without
  casting.
- **Dates are ISO 8601 strings** in transit, `null`/`undefined` for absent
  optional fields (the OpenAPI document does not mark properties as required,
  so generated fields are optional — narrow before use).
- Regenerate rather than hand-edit `src/generated/`: local changes are lost.
