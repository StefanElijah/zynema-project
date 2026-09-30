import { useEffect } from 'react';
import { useAuth } from 'react-oidc-context';
import { useProfiles, type Profile } from '@lib/api';
import { useProfileStore } from '@stores/useProfileStore';

export interface SelectedProfile {
  profiles: Profile[];
  selected: Profile | null;
  isLoading: boolean;
}

/**
 * The account's profiles plus the one that is selected, kept consistent:
 *
 * - the list is only fetched when there is a session (the endpoint is private),
 * - the first profile is selected automatically when none is,
 * - a selection that no longer exists (profile deleted elsewhere) is replaced
 *   instead of silently sending requests for a ghost profile.
 */
export function useSelectedProfile(): SelectedProfile {
  const auth = useAuth();
  const selectedProfileId = useProfileStore((state) => state.selectedProfileId);
  const select = useProfileStore((state) => state.select);

  const { data: profiles = [], isLoading } = useProfiles({
    query: { enabled: auth.isAuthenticated },
  });

  useEffect(() => {
    // The generated types keep properties optional (the OpenAPI document does
    // not mark them required); an id-less profile is not selectable at all.
    const first = profiles[0];
    if (isLoading || !first?.id) {
      return;
    }
    if (!profiles.some((profile) => profile.id === selectedProfileId)) {
      select(first.id);
    }
  }, [profiles, isLoading, selectedProfileId, select]);

  return {
    profiles,
    selected: profiles.find((profile) => profile.id === selectedProfileId) ?? null,
    isLoading,
  };
}
