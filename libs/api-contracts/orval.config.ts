import { defineConfig } from 'orval';

/**
 * The SPA's typed surface, generated from the BFF's OpenAPI spec.
 *
 * Input is the committed snapshot (`openapi.json`) so `pnpm api:generate` is
 * deterministic and works offline; `pnpm api:refresh` pulls a fresh copy from a
 * running BFF and both live in `package.json` so the loop is one command.
 *
 * Output is a single React Query client: the hooks call the BFF through the
 * axios mutator, which is the instance the app configures with its base URL,
 * token interceptor and 401-renewal. That is why the error type is bound to
 * the platform's error envelope instead of a bare `AxiosError<unknown>`.
 */
export default defineConfig({
  zynema: {
    input: {
      target: './openapi.json',
    },
    output: {
      target: './src/generated/zynema.ts',
      client: 'react-query',
      httpClient: 'axios',
      mode: 'single',
      clean: true,
      // Formatting is owned by the repo's prettier (lint-staged on commit),
      // not by a second copy inside orval.
      prettier: false,
      override: {
        mutator: {
          path: './src/mutator.ts',
          name: 'customInstance',
        },
        query: {
          // Orval's defaults are what we want: GET becomes useQuery, the rest
          // becomes useMutation. Only cancellation needs enabling.
          signal: true,
        },
      },
    },
  },
});
