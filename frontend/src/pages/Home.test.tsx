import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Home from './Home';

const useHome = vi.fn();

vi.mock('@lib/api', () => ({ useHome: () => useHome() }));
vi.mock('@hooks/useWatchlistActions', () => ({
  useWatchlistActions: () => ({
    canManage: false,
    add: vi.fn(),
    remove: vi.fn(),
    isPending: false,
    error: null,
  }),
}));

const HOME = {
  hero: { card: { id: 'a2', slug: 'arcane', title: 'Arcane' }, synopsis: 'Piltover y Zaun.' },
  rows: [
    {
      id: 'popular-series',
      title: 'Series populares',
      items: [{ id: 'a2', title: 'Arcane', releaseYear: 2021 }],
    },
  ],
};

describe('Home', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows skeletons while loading', () => {
    useHome.mockReturnValue({ isLoading: true });

    const { container } = renderWithProviders(<Home />);

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
    expect(screen.queryByRole('heading', { name: 'Arcane' })).not.toBeInTheDocument();
  });

  it('offers a retry when the catalogue is down', async () => {
    const refetch = vi.fn();
    useHome.mockReturnValue({ isLoading: false, isError: true, refetch });
    const user = userEvent.setup();

    renderWithProviders(<Home />);
    expect(screen.getByText('No pudimos cargar el catálogo.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(refetch).toHaveBeenCalled();
  });

  it('renders the hero and the rails the BFF composed', () => {
    useHome.mockReturnValue({ isLoading: false, data: HOME });

    renderWithProviders(<Home />);

    expect(screen.getByRole('heading', { name: 'Arcane', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Piltover y Zaun.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Series populares' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Arcane' })).toBeInTheDocument();
  });
});
