import { beforeEach, describe, expect, it } from 'vitest';
import { useProfileStore } from './useProfileStore';

const DEMO_PROFILE = '72000000-0000-4000-8000-000000000001';

describe('useProfileStore', () => {
  beforeEach(() => {
    useProfileStore.getState().clear();
    localStorage.clear();
  });

  it('remembers the selected profile', () => {
    useProfileStore.getState().select(DEMO_PROFILE);

    expect(useProfileStore.getState().selectedProfileId).toBe(DEMO_PROFILE);
  });

  it('persists the selection so a reload does not lose who is watching', () => {
    useProfileStore.getState().select(DEMO_PROFILE);

    expect(localStorage.getItem('zynema.profile')).toContain(DEMO_PROFILE);
  });

  it('clears the selection on sign out', () => {
    useProfileStore.getState().select(DEMO_PROFILE);

    useProfileStore.getState().clear();

    expect(useProfileStore.getState().selectedProfileId).toBeNull();
  });
});
