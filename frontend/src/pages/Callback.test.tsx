import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Callback from './Callback';

const authMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));

vi.mock('react-oidc-context', () => ({
  useAuth: () => authMock.current,
}));

describe('Callback', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('tells the visitor the sign-in is completing', () => {
    authMock.current = { isLoading: true, isAuthenticated: false };
    renderWithProviders(<Callback />, { route: '/callback?code=authorization-code' });

    expect(screen.getByText('Completando inicio de sesión…')).toBeInTheDocument();
  });

  it('returns to the requested internal route after successful sign-in', async () => {
    authMock.current = {
      isLoading: false,
      isAuthenticated: true,
      user: { state: { returnTo: '/account?section=profiles' } },
    };

    renderWithProviders(<Callback />, { route: '/callback?code=authorization-code' });

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/account?section=profiles')
    );
  });

  it('returns home when no destination was requested', async () => {
    authMock.current = { isLoading: false, isAuthenticated: true, user: { state: undefined } };

    renderWithProviders(<Callback />, { route: '/callback?code=authorization-code' });

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'));
  });

  it('shows a readable message when OIDC callback processing fails', () => {
    authMock.current = { isLoading: false, isAuthenticated: false, error: new Error('OIDC error') };

    renderWithProviders(<Callback />, { route: '/callback?error=access_denied' });

    expect(screen.getByRole('alert')).toHaveTextContent(
      'No se pudo completar el inicio de sesión.'
    );
    expect(screen.getByText('OIDC error')).toBeInTheDocument();
  });
});
