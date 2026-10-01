import { type ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useWatchlistActions } from './useWatchlistActions';

interface MutationOptions {
  mutation?: { onSuccess?: () => void };
}

const mocks = vi.hoisted(() => ({
  addMutate: vi.fn(),
  removeMutate: vi.fn(),
  addOptions: undefined as MutationOptions | undefined,
  selected: undefined as { id: string } | undefined,
}));

vi.mock('@lib/api', () => ({
  getProfileHomeQueryKey: (profileId: string) => ['/api/v1/web/profiles', profileId, 'home'],
  useAddToWatchlist: (options?: MutationOptions) => {
    mocks.addOptions = options;
    return { mutate: mocks.addMutate, isPending: false, error: null };
  },
  useRemoveFromWatchlist: () => ({
    mutate: mocks.removeMutate,
    isPending: false,
    error: null,
  }),
}));

vi.mock('@hooks/useSelectedProfile', () => ({
  useSelectedProfile: () => ({ profiles: [], selected: mocks.selected, isLoading: false }),
}));

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe('useWatchlistActions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.selected = undefined;
  });

  it('does nothing without a selected profile', () => {
    const client = new QueryClient();
    const { result } = renderHook(() => useWatchlistActions(), { wrapper: wrapper(client) });

    expect(result.current.canManage).toBe(false);
    act(() => result.current.add('content-1'));
    expect(mocks.addMutate).not.toHaveBeenCalled();
  });

  it('adds and removes with the selected profile', () => {
    mocks.selected = { id: 'profile-1' };
    const client = new QueryClient();
    const { result } = renderHook(() => useWatchlistActions(), { wrapper: wrapper(client) });

    expect(result.current.canManage).toBe(true);
    act(() => result.current.add('content-1'));
    expect(mocks.addMutate).toHaveBeenCalledWith({
      profileId: 'profile-1',
      data: { contentId: 'content-1' },
    });
    act(() => result.current.remove('content-2'));
    expect(mocks.removeMutate).toHaveBeenCalledWith({
      profileId: 'profile-1',
      contentId: 'content-2',
    });
  });

  it('refreshes the profile rails after a change', () => {
    mocks.selected = { id: 'profile-1' };
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries');

    renderHook(() => useWatchlistActions(), { wrapper: wrapper(client) });
    act(() => {
      mocks.addOptions?.mutation?.onSuccess?.();
    });

    expect(invalidate).toHaveBeenCalledWith({
      queryKey: ['/api/v1/web/profiles', 'profile-1', 'home'],
    });
  });
});
