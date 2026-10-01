import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Account from './Account';

interface MutationOptions {
  mutation?: { onSuccess?: () => void };
}

const mocks = vi.hoisted(() => ({
  account: {} as Record<string, unknown>,
  createMutate: vi.fn(),
  deleteMutate: vi.fn(),
  createOptions: undefined as MutationOptions | undefined,
  createError: null as unknown,
  deleteError: null as unknown,
}));

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ user: { profile: { preferred_username: 'demo' } } }),
}));

vi.mock('@lib/api', () => ({
  useAccount: () => ({
    data: mocks.account,
    isLoading: mocks.account.loading === true,
    isError: mocks.account.error === true,
    refetch: mocks.account.refetch ?? vi.fn(),
  }),
  useCreateProfile: (options?: MutationOptions) => {
    mocks.createOptions = options;
    return { mutate: mocks.createMutate, isPending: false, error: mocks.createError };
  },
  useDeleteProfile: () => ({
    mutate: mocks.deleteMutate,
    isPending: false,
    error: mocks.deleteError,
  }),
  getProfilesQueryKey: () => ['/api/v1/web/profiles'],
  getAccountQueryKey: () => ['/api/v1/web/account'],
}));

const ACCOUNT = {
  identity: { username: 'demo', email: 'demo@zynema.dev', emailVerified: true, roles: ['user'] },
  user: { email: 'demo@zynema.dev', displayName: 'Demo', preferredLanguage: 'es', profiles: [] },
  degraded: [],
};

describe('Account', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.account = { ...ACCOUNT };
    mocks.createError = null;
    mocks.deleteError = null;
  });

  it('waits with a skeleton', () => {
    mocks.account = { loading: true };

    const { container } = renderWithProviders(<Account />);

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('offers a retry when the account cannot be loaded', async () => {
    const refetch = vi.fn();
    mocks.account = { error: true, refetch };
    const user = userEvent.setup();

    renderWithProviders(<Account />);
    expect(screen.getByText('No se pudo cargar la cuenta.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(refetch).toHaveBeenCalled();
  });

  it('warns about degraded sections', () => {
    mocks.account = { ...ACCOUNT, degraded: ['PLAYBACK'] };

    renderWithProviders(<Account />);

    expect(
      screen.getByText(/Algunas secciones no están disponibles ahora: PLAYBACK/)
    ).toBeInTheDocument();
  });

  it('shows the active subscription with its renewal date', () => {
    mocks.account = {
      ...ACCOUNT,
      subscription: {
        plan: { name: 'Standard' },
        status: 'ACTIVE',
        currentPeriodEnd: '2026-12-01T00:00:00Z',
      },
      entitlements: { active: true, planCode: 'standard', maxQuality: 'FHD', maxStreams: 2 },
    };

    renderWithProviders(<Account />);

    expect(screen.getByText('Standard')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    expect(screen.getByText('FHD')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.queryByText(/No tenés una suscripción activa/)).not.toBeInTheDocument();
  });

  it('deletes a profile', async () => {
    mocks.account = {
      ...ACCOUNT,
      user: {
        ...ACCOUNT.user,
        profiles: [{ id: 'p2', name: 'Kids', kids: true, language: 'es' }],
      },
    };
    const user = userEvent.setup();

    renderWithProviders(<Account />);
    expect(screen.getByText('Kids · infantil')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Eliminar' }));
    expect(mocks.deleteMutate).toHaveBeenCalledWith({ profileId: 'p2' });
  });

  it('creates a profile and refreshes the account', async () => {
    const user = userEvent.setup();

    const { queryClient } = renderWithProviders(<Account />);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    await user.type(screen.getByLabelText('Nuevo perfil'), 'Mate');
    await user.click(screen.getByRole('checkbox', { name: 'Infantil' }));
    await user.click(screen.getByRole('button', { name: 'Crear' }));

    expect(mocks.createMutate).toHaveBeenCalledWith(
      { data: { name: 'Mate', kids: true, language: 'es' } },
      expect.objectContaining({ onSuccess: expect.any(Function) })
    );

    mocks.createOptions?.mutation?.onSuccess?.();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['/api/v1/web/profiles'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['/api/v1/web/account'] });
  });

  it('shows what the server said when a profile cannot be created', () => {
    mocks.createError = { response: { data: { message: 'Ya existe un perfil con ese nombre' } } };

    renderWithProviders(<Account />);

    expect(screen.getByText('Ya existe un perfil con ese nombre')).toBeInTheDocument();
  });

  it('falls back to a generic message when deleting fails', () => {
    mocks.deleteError = { response: { data: {} } };

    renderWithProviders(<Account />);

    expect(screen.getByText('No se pudo eliminar el perfil.')).toBeInTheDocument();
  });
});
