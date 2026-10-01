import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Plans from './Plans';

const usePlans = vi.fn();
const useAccount = vi.fn();
const useSubscribe = vi.fn();
const authMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));

vi.mock('@lib/api', () => ({
  usePlans: () => usePlans(),
  useAccount: (options: unknown) => useAccount(options),
  useSubscribe: (options: unknown) => useSubscribe(options),
  getAccountQueryKey: () => ['/api/v1/web/account'],
}));

vi.mock('react-oidc-context', () => ({ useAuth: () => authMock.current }));

const PLANS = [
  {
    id: 'p1',
    code: 'basic',
    name: 'Basic',
    price: 4.99,
    currency: 'EUR',
    billingPeriod: 'MONTHLY',
    maxStreams: 1,
    maxQuality: 'HD',
  },
  {
    id: 'p2',
    code: 'standard',
    name: 'Standard',
    price: 9.99,
    currency: 'EUR',
    billingPeriod: 'MONTHLY',
    maxStreams: 2,
    maxQuality: 'FHD',
  },
];

function subscribeState(overrides: Record<string, unknown> = {}) {
  return {
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
    isError: false,
    data: undefined,
    error: null,
    ...overrides,
  };
}

describe('Plans', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMock.current = { isAuthenticated: false, signinRedirect: vi.fn() };
    usePlans.mockReturnValue({ isLoading: false, data: PLANS });
    useAccount.mockReturnValue({ data: undefined });
    useSubscribe.mockReturnValue(subscribeState());
  });

  it('shows the plans with their prices', () => {
    renderWithProviders(<Plans />, { route: '/plans' });

    expect(screen.getByRole('heading', { name: 'Elegí tu plan' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Basic' })).toBeInTheDocument();
    expect(screen.getByText(/4,99/)).toBeInTheDocument();
    expect(screen.getByText('1 pantalla(s) a la vez')).toBeInTheDocument();
  });

  it('sends a visitor through the login before checkout', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Plans />, { route: '/plans' });

    await user.click(screen.getAllByRole('button', { name: 'Iniciar sesión para suscribirme' })[0]);
    expect(authMock.current.signinRedirect).toHaveBeenCalled();
  });

  it('subscribes with the plan and the idempotent payment method', async () => {
    const mutate = vi.fn();
    authMock.current = { isAuthenticated: true, signinRedirect: vi.fn() };
    useSubscribe.mockReturnValue(subscribeState({ mutate }));
    const user = userEvent.setup();

    renderWithProviders(<Plans />, { route: '/plans' });
    await user.click(screen.getAllByRole('button', { name: 'Suscribirme' })[1]);

    expect(mutate).toHaveBeenCalledWith({ data: { planId: 'p2', paymentMethod: 'card' } });
  });

  it('confirms a successful subscription', () => {
    useSubscribe.mockReturnValue(
      subscribeState({ isSuccess: true, data: { plan: { name: 'Standard' } } })
    );

    renderWithProviders(<Plans />, { route: '/plans' });

    expect(screen.getByText('¡Listo! Tu plan Standard está activo.')).toBeInTheDocument();
  });

  it('shows the message payment rejected', () => {
    useSubscribe.mockReturnValue(
      subscribeState({
        isError: true,
        error: { response: { data: { message: 'Ya tenés una suscripción activa' } } },
      })
    );

    renderWithProviders(<Plans />, { route: '/plans' });

    expect(screen.getByText('Ya tenés una suscripción activa')).toBeInTheDocument();
  });

  it('marks the current plan', () => {
    authMock.current = { isAuthenticated: true, signinRedirect: vi.fn() };
    useAccount.mockReturnValue({ data: { entitlements: { planCode: 'basic' } } });

    renderWithProviders(<Plans />, { route: '/plans' });

    expect(screen.getByRole('button', { name: 'Tu plan' })).toBeDisabled();
  });

  it('shows skeletons while the plans load', () => {
    usePlans.mockReturnValue({ isLoading: true });

    const { container } = renderWithProviders(<Plans />, { route: '/plans' });

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('offers a retry when the plans cannot be loaded', async () => {
    const refetch = vi.fn();
    usePlans.mockReturnValue({ isLoading: false, isError: true, refetch });
    const user = userEvent.setup();

    renderWithProviders(<Plans />, { route: '/plans' });
    expect(screen.getByText('No pudimos cargar los planes.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(refetch).toHaveBeenCalled();
  });

  it('shows a dash when a plan has no price', () => {
    usePlans.mockReturnValue({
      isLoading: false,
      data: [{ id: 'p4', code: 'free', name: 'Free', billingPeriod: 'MONTHLY' }],
    });

    renderWithProviders(<Plans />, { route: '/plans' });

    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('prices a yearly plan by the year', () => {
    usePlans.mockReturnValue({
      isLoading: false,
      data: [
        {
          id: 'p3',
          code: 'annual',
          name: 'Annual',
          price: 99,
          currency: 'EUR',
          billingPeriod: 'YEARLY',
          maxStreams: 2,
          maxQuality: 'FHD',
        },
      ],
    });

    renderWithProviders(<Plans />, { route: '/plans' });

    expect(screen.getByText(/99,00\/año/)).toBeInTheDocument();
  });

  it('refreshes the account after a successful subscription', () => {
    let options: { mutation?: { onSuccess?: () => void } } | undefined;
    useSubscribe.mockImplementation((received: typeof options) => {
      options = received;
      return subscribeState();
    });

    const { queryClient } = renderWithProviders(<Plans />, { route: '/plans' });
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    options?.mutation?.onSuccess?.();

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['/api/v1/web/account'] });
  });
});
