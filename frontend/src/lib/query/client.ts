import { QueryClient } from '@tanstack/react-query';

/**
 * Query defaults for a streaming UI.
 *
 * Five minutes of staleness keeps navigation instant without freezing the
 * catalogue: sections change rarely, the queryKey carries the parameters, and
 * mutations invalidate what they touched instead of relying on refetch storms.
 * Focus refetching is off because the player and the rails own their own
 * refresh moments; a retry is left on once for genuinely transient failures.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      gcTime: 10 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
