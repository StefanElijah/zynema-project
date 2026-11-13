import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RequireAuth from './RequireAuth';

const authMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));

vi.mock('react-oidc-context', () => ({
  useAuth: () => authMock.current,
}));

function renderGate(route = '/account') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <RequireAuth>
        <p>contenido</p>
      </RequireAuth>
    </MemoryRouter>
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('waits while the session is being verified', () => {
    authMock.current = { isLoading: true };

    renderGate();

    expect(screen.getByText('Verificando sesión…')).toBeInTheDocument();
    expect(screen.queryByText('contenido')).not.toBeInTheDocument();
  });

  it('reports an auth error and offers to retry', async () => {
    const signinRedirect = vi.fn();
    authMock.current = { isLoading: false, error: new Error('boom'), signinRedirect };
    const user = userEvent.setup();

    renderGate('/account?tab=profiles');

    expect(screen.getByText('No se pudo verificar la sesión.')).toBeInTheDocument();
    expect(screen.getByText('boom')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Volver a intentar' }));
    expect(signinRedirect).toHaveBeenCalledWith({
      state: { returnTo: '/account?tab=profiles' },
    });
  });

  it('invites an anonymous visitor to sign in', async () => {
    const signinRedirect = vi.fn();
    authMock.current = { isLoading: false, isAuthenticated: false, signinRedirect };
    const user = userEvent.setup();

    renderGate('/account?tab=profiles');

    expect(screen.getByRole('heading', { name: 'Necesitas iniciar sesión' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
    expect(signinRedirect).toHaveBeenCalledWith({
      state: { returnTo: '/account?tab=profiles' },
    });
  });

  it('renders the children for an authenticated user', () => {
    authMock.current = { isLoading: false, isAuthenticated: true };

    renderGate();

    expect(screen.getByText('contenido')).toBeInTheDocument();
  });
});
