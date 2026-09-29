import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { AXIOS_INSTANCE } from '@zynema/api-contracts';
import { useProfileStore } from '@stores/useProfileStore';
import AppNavbar from './AppNavbar';

const authMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));

vi.mock('react-oidc-context', () => ({
  useAuth: () => authMock.current,
}));

vi.mock('@lib/auth/userManager', () => ({
  userManager: {
    getUser: vi.fn().mockResolvedValue(null),
    signinSilent: vi.fn(),
    signinRedirect: vi.fn(),
  },
}));

const DEMO_PROFILE = '72000000-0000-4000-8000-000000000001';
const KIDS_PROFILE = '72000000-0000-4000-8000-000000000002';

const PROFILES = [
  { id: DEMO_PROFILE, name: 'Demo', kids: false, language: 'es' },
  { id: KIDS_PROFILE, name: 'Kids', kids: true, language: 'es' },
];

const originalAdapter = AXIOS_INSTANCE.defaults.adapter;

function asResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config };
}

function renderNavbar() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AppNavbar />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('AppNavbar', () => {
  beforeEach(() => {
    useProfileStore.getState().clear();
    // The generated hooks go through the real axios instance: serving the
    // profiles from its adapter keeps the test on the production code path.
    AXIOS_INSTANCE.defaults.adapter = async (config) => asResponse(config, PROFILES);
  });

  afterEach(() => {
    AXIOS_INSTANCE.defaults.adapter = originalAdapter;
    vi.clearAllMocks();
  });

  it('offers sign in to a visitor', () => {
    authMock.current = { isLoading: false, isAuthenticated: false, signinRedirect: vi.fn() };

    renderNavbar();

    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument();
  });

  it('selects the first profile once the account has one', async () => {
    authMock.current = {
      isLoading: false,
      isAuthenticated: true,
      user: { profile: { preferred_username: 'demo' } },
      signoutRedirect: vi.fn(),
    };

    renderNavbar();

    await waitFor(() => expect(useProfileStore.getState().selectedProfileId).toBe(DEMO_PROFILE));
  });

  it('switches profiles from the menu and clears the choice on sign out', async () => {
    // Signing out ends the session (in the browser the redirect unloads the
    // page; here the flag flip is what the component sees).
    const signoutRedirect = vi.fn(() => {
      authMock.current = { ...authMock.current, isAuthenticated: false };
    });
    authMock.current = {
      isLoading: false,
      isAuthenticated: true,
      user: { profile: { preferred_username: 'demo' } },
      signoutRedirect,
    };
    const user = userEvent.setup();

    renderNavbar();
    await waitFor(() => expect(useProfileStore.getState().selectedProfileId).toBe(DEMO_PROFILE));

    await user.click(screen.getByRole('button', { name: 'Perfil' }));
    await user.click(await screen.findByRole('menuitem', { name: /Kids/ }));
    expect(useProfileStore.getState().selectedProfileId).toBe(KIDS_PROFILE);

    await user.click(screen.getByRole('button', { name: 'Perfil' }));
    await user.click(await screen.findByRole('menuitem', { name: /Salir/ }));
    expect(useProfileStore.getState().selectedProfileId).toBeNull();
    expect(signoutRedirect).toHaveBeenCalled();
  });
});
