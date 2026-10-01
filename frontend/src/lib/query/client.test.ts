import { describe, expect, it } from 'vitest';
import { queryClient } from './client';

describe('queryClient', () => {
  it('is tuned for a streaming UI', () => {
    const queries = queryClient.getDefaultOptions().queries;

    expect(queries?.staleTime).toBe(5 * 60 * 1000);
    expect(queries?.gcTime).toBe(10 * 60 * 1000);
    expect(queries?.retry).toBe(1);
    expect(queries?.refetchOnWindowFocus).toBe(false);
  });
});
