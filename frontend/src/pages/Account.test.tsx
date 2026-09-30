import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Account from './Account';

const api = vi.hoisted(() => ({
  createProfile: vi.fn(),
  deleteProfile: vi.fn(),
  account: {} as Record<string, unknown>,
}));

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ user: { profile: { preferred_username: 'demo' } } }),
}));

vi.mock('@lib/api', () => ({
  useAccount: () => ({
    data: api.account,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useCreateProfile: () => ({ mutate: api.createProfile, isPending: false, error: null }),
  useDeleteProfile: () => ({ mutate: api.deleteProfile, isPending: false, error: null }),
  getProfilesQueryKey: () => ['/api/v1/web/profiles'],
  getAccountQueryKey: () => ['/api/v1/web/account'],
}));

const ACCOUNT = {
  identity: { username: 'demo', email: 'demo@zynema.dev' },
  user: { email: 'demo@zynema.dev', displayName: 'Demo', profiles: [] },
  degraded: [],
};

function renderAccount() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Account />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('Account profile form', () => {
  beforeEach(() => {
    api.account = ACCOUNT;
    vi.clearAllMocks();
  });

  it('refuses an empty name before calling the API', async () => {
    const user = userEvent.setup();
    renderAccount();

    await user.click(screen.getByRole('button', { name: 'Crear' }));

    expect(await screen.findByText('Ingresá un nombre.')).toBeInTheDocument();
    expect(api.createProfile).not.toHaveBeenCalled();
  });

  it('submits the trimmed values the API expects', async () => {
    const user = userEvent.setup();
    renderAccount();

    await user.type(screen.getByLabelText('Nuevo perfil'), '  Mate  ');
    await user.click(screen.getByRole('checkbox', { name: 'Infantil' }));
    await user.click(screen.getByRole('button', { name: 'Crear' }));

    expect(api.createProfile).toHaveBeenCalledWith(
      { data: { name: 'Mate', kids: true, language: 'es' } },
      expect.anything()
    );
  });
});
