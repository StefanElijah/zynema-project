# Zynema — frontend

The SPA. React 19 + Vite + TypeScript (strict) + Tailwind v4, with TanStack
Query over a client generated from the BFF's OpenAPI document. The BFF is the
only backend this app talks to (ADR-0004); the gateway is the only entry point.

## Scripts (pnpm only)

```bash
pnpm dev            # Vite dev server on :5173, /api proxied to the gateway (:8080)
pnpm build          # production build
pnpm typecheck      # tsc --noEmit (strict)
pnpm lint           # eslint, zero warnings
pnpm test           # Vitest (jsdom) unit and component tests
pnpm test:e2e       # Playwright; specs stub the BFF, no stack required
```

Auth E2E is opt-in (`E2E_AUTH=1`) because it drives real Keycloak; everything
else runs against stubbed `/api/v1/web/**` responses.

## API layer

- The typed client lives in [`libs/api-contracts`](../libs/api-contracts): orval
  generates it from the committed `openapi.json` snapshot, react-query hooks
  included. Regenerate with `pnpm api:refresh` (needs a running BFF) then
  `pnpm api:generate`.
- `src/lib/api/client.ts` configures the one axios instance the generated code
  uses: base URL, bearer token, single 401 renewal. Import hooks from
  `@lib/api` (the wrapper) so that configuration always runs.
- `src/lib/api/playback.ts` keeps the player's session lifecycle with its
  failure mapping (paywall, not-ready, stream limit); it is the one hand-written
  transport and has its own tests.

## Structure

```
src/
├── components/ui/       # shadcn-style primitives (Tailwind v4 theme)
├── components/          # molecules and organisms of the app
├── hooks/               # selected profile, watchlist actions
├── lib/api|auth|query/  # transport, OIDC session, query defaults
├── pages/               # one file per route (see App.tsx)
└── stores/              # Zustand: the selected profile (persisted)
```

Environment variables (`VITE_API_BASE_URL`, OIDC authority/client) are
documented in [`.env.example`](./.env.example).
