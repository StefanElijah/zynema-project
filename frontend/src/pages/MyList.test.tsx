import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import MyList from './MyList';

const useProfileHome = vi.fn();
const remove = vi.fn();
const profileMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));

vi.mock('@lib/api', () => ({
  useProfileHome: (profileId: string, options: unknown) => useProfileHome(profileId, options),
}));

vi.mock('@hooks/useSelectedProfile', () => ({
  useSelectedProfile: () => profileMock.current,
}));

vi.mock('@hooks/useWatchlistActions', () => ({
  useWatchlistActions: () => ({
    canManage: true,
    add: vi.fn(),
    remove,
    isPending: false,
    error: null,
  }),
}));

const HOME = {
  profileId: 'p1',
  continueWatching: [{ content: { id: 'a1', title: 'Dune' } }],
  myList: [{ content: { id: 'a2', title: 'Arcane' } }],
  degraded: [],
};

describe('MyList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    profileMock.current = { profiles: [], selected: { id: 'p1', name: 'Demo' }, isLoading: false };
    useProfileHome.mockReturnValue({ isLoading: false, data: HOME });
  });

  it('asks for a profile when none is selected', () => {
    profileMock.current = { profiles: [], selected: undefined, isLoading: false };

    renderWithProviders(<MyList />, { route: '/my-list' });

    expect(
      screen.getByText('Elegí un perfil en la barra superior para ver tu lista.')
    ).toBeInTheDocument();
  });

  it('shows both rails with their titles', () => {
    renderWithProviders(<MyList />, { route: '/my-list' });

    expect(screen.getByRole('heading', { name: 'Mi Lista', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Dune' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Arcane' })).toBeInTheDocument();
  });

  it('removes a title from the list', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MyList />, { route: '/my-list' });

    await user.click(screen.getByRole('button', { name: 'Quitar' }));
    expect(remove).toHaveBeenCalledWith('a2');
  });

  it('explains the empty rails', () => {
    useProfileHome.mockReturnValue({
      isLoading: false,
      data: { profileId: 'p1', continueWatching: [], myList: [], degraded: [] },
    });

    renderWithProviders(<MyList />, { route: '/my-list' });

    expect(screen.getByText('Todavía no empezaste nada.')).toBeInTheDocument();
    expect(
      screen.getByText('Agregá títulos con “Mi Lista” desde cualquier tarjeta.')
    ).toBeInTheDocument();
  });

  it('warns about degraded sections', () => {
    useProfileHome.mockReturnValue({ isLoading: false, data: { ...HOME, degraded: ['MY_LIST'] } });

    renderWithProviders(<MyList />, { route: '/my-list' });

    expect(screen.getByText(/Algunas secciones no están disponibles ahora/)).toBeInTheDocument();
  });

  it('shows skeletons while the rails load', () => {
    profileMock.current = { profiles: [], selected: undefined, isLoading: true };

    const { container } = renderWithProviders(<MyList />, { route: '/my-list' });

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });
});
