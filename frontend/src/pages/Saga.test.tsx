import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Saga from './Saga';

const useFranchise = vi.fn();

vi.mock('@lib/api', () => ({
  useFranchise: (...args: unknown[]) => useFranchise(...args),
}));

vi.mock('@hooks/useWatchlistActions', () => ({
  useWatchlistActions: () => ({
    canManage: false,
    add: vi.fn(),
    remove: vi.fn(),
    isPending: false,
    error: null,
  }),
}));

const SAGA = {
  id: 'f1',
  slug: 'star-wars-skywalker-saga',
  name: 'Star Wars: Skywalker Saga',
  description: 'The six-film saga.',
  coverUrl: '/assets/movies/images/star-wars.jpg',
  items: [
    {
      id: 'a37',
      type: 'MOVIE',
      slug: 'star-wars-episode-i-the-phantom-menace',
      title: 'Star Wars: Episode I - The Phantom Menace',
      posterUrl: '/ep1.jpg',
    },
    {
      id: 'a38',
      type: 'MOVIE',
      slug: 'star-wars-episode-ii-attack-of-the-clones',
      title: 'Star Wars: Episode II - Attack of the Clones',
      posterUrl: '/ep2.jpg',
    },
  ],
};

describe('Saga', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the saga and keeps the curated order', () => {
    useFranchise.mockReturnValue({ isLoading: false, data: SAGA });

    renderWithProviders(<Saga />, { route: '/saga/star-wars-skywalker-saga' });

    expect(screen.getByRole('heading', { name: 'Star Wars: Skywalker Saga' })).toBeInTheDocument();
    expect(screen.getByText('2 títulos en orden')).toBeInTheDocument();

    const images = screen.getAllByRole('img');
    expect(images.map((image) => image.getAttribute('alt'))).toEqual([
      'Star Wars: Episode I - The Phantom Menace',
      'Star Wars: Episode II - Attack of the Clones',
    ]);
  });

  it('shows the not-found state and retries', async () => {
    const refetch = vi.fn();
    useFranchise.mockReturnValue({ isLoading: false, isError: true, refetch });
    const user = userEvent.setup();

    renderWithProviders(<Saga />, { route: '/saga/nope' });

    expect(screen.getByText('No encontramos esa saga.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(refetch).toHaveBeenCalled();
  });

  it('shows skeletons while loading', () => {
    useFranchise.mockReturnValue({ isLoading: true, data: undefined });

    const { container } = renderWithProviders(<Saga />, { route: '/saga/dune' });

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });
});
