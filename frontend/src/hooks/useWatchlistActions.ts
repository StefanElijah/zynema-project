import { useQueryClient } from '@tanstack/react-query';
import {
  getProfileHomeQueryKey,
  useAddToWatchlist,
  useRemoveFromWatchlist,
  type ErrorType,
} from '@lib/api';
import { useSelectedProfile } from '@hooks/useSelectedProfile';

export interface WatchlistActions {
  /** False when there is no profile to add to; the UI asks for one first. */
  canManage: boolean;
  add: (contentId: string | undefined) => void;
  remove: (contentId: string | undefined) => void;
  isPending: boolean;
  error: ErrorType<unknown> | null;
}

/**
 * "Mi Lista" on any card, from any page.
 *
 * The watchlist belongs to a profile, and the selected profile is global
 * state, so the action is one hook instead of each page wiring the mutation.
 * Success refreshes the rails (`/profiles/{id}/home`) so the list the user
 * just changed is the list they see.
 */
export function useWatchlistActions(): WatchlistActions {
  const queryClient = useQueryClient();
  const { selected } = useSelectedProfile();
  const profileId = selected?.id;

  const invalidate = () => {
    if (profileId) {
      void queryClient.invalidateQueries({ queryKey: getProfileHomeQueryKey(profileId) });
    }
  };

  const addMutation = useAddToWatchlist({ mutation: { onSuccess: invalidate } });
  const removeMutation = useRemoveFromWatchlist({ mutation: { onSuccess: invalidate } });

  return {
    canManage: Boolean(profileId),
    add: (contentId) => {
      if (profileId && contentId) {
        addMutation.mutate({ profileId, data: { contentId } });
      }
    },
    remove: (contentId) => {
      if (profileId && contentId) {
        removeMutation.mutate({ profileId, contentId });
      }
    },
    isPending: addMutation.isPending || removeMutation.isPending,
    error: (addMutation.error ?? removeMutation.error) as ErrorType<unknown> | null,
  };
}
