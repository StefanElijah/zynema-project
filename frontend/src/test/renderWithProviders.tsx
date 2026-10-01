import { type ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import LocationProbe from './LocationProbe';

interface Options {
  route?: string;
}

/**
 * Renders with the providers every page needs (fresh QueryClient, router) and
 * exposes the current location, so navigation and URL-state assertions are one
 * `expect` away.
 */
export function renderWithProviders(ui: ReactElement, { route = '/' }: Options = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        {ui}
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>
  );

  return { ...result, queryClient };
}
