import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface ProfileState {
  selectedProfileId: string | null;
  select: (profileId: string) => void;
  clear: () => void;
}

/**
 * The profile the app is watching as. It outlives reloads (persisted to
 * localStorage) because picking "who is watching" is a deliberate choice, and
 * it is cleared on sign-out so the next account never inherits it.
 *
 * This is the one piece of client state that is not server state: everything
 * else lives in React Query, keyed by request.
 */
export const useProfileStore = create<ProfileState>()(
  persist(
    (set) => ({
      selectedProfileId: null,
      select: (selectedProfileId) => set({ selectedProfileId }),
      clear: () => set({ selectedProfileId: null }),
    }),
    { name: 'zynema.profile' }
  )
);
