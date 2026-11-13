import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Home from './Home';

const useHome = vi.fn();
const useFranchises = vi.fn();

vi.mock('@lib/api', () => ({
  useHome: () => useHome(),
  useFranchises: () => useFranchises(),
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

const HOME = {
  hero: { card: { id: 'a2', slug: 'arcane', title: 'Arcane' }, synopsis: 'Piltover y Zaun.' },
  rows: [
    {
      id: 'new-releases',
      title: 'Estrenos',
      items: [{ id: 'a3', title: 'Dune: Part Two', releaseYear: 2024 }],
    },
    {
      id: 'popular-series',
      title: 'Series populares',
      items: [{ id: 'a2', title: 'Arcane', releaseYear: 2021 }],
    },
    {
      id: 'popular-movies',
      title: 'Películas populares',
      items: [{ id: 'a3', title: 'Dune: Part Two', releaseYear: 2024 }],
    },
  ],
};

describe('Home', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useFranchises.mockReturnValue({ data: [] });
  });

  it('renders the saga section from the public franchise endpoint', () => {
    useHome.mockReturnValue({ isLoading: false, data: HOME });
    useFranchises.mockReturnValue({
      data: [
        {
          id: 'f1',
          slug: 'the-dark-knight-trilogy',
          name: 'The Dark Knight Trilogy',
          description: 'The complete trilogy.',
          coverUrl: '/assets/movies/images/trilogy.jpg',
          itemCount: 3,
        },
      ],
    });

    renderWithProviders(<Home />);

    expect(screen.getByRole('heading', { name: 'Sagas y franquicias' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /The Dark Knight Trilogy/ })).toHaveAttribute(
      'href',
      '/saga/the-dark-knight-trilogy'
    );
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

  it('reports and retries an invalid successful response instead of rendering an empty page', async () => {
    const refetch = vi.fn();
    useHome.mockReturnValue({
      isLoading: false,
      data: '<!doctype html><html></html>',
      refetch,
    });
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

  it('sends each typed rail to the catalogue and leaves the mixed rail without a link', () => {
    useHome.mockReturnValue({ isLoading: false, data: HOME });

    renderWithProviders(<Home />);

    expect(screen.getByRole('link', { name: 'Explorar catálogo' })).toHaveAttribute(
      'href',
      '/catalog'
    );
    expect(screen.getByRole('link', { name: 'Ver todo Series populares' })).toHaveAttribute(
      'href',
      '/catalog?type=SERIES'
    );
    expect(screen.getByRole('link', { name: 'Ver todo Películas populares' })).toHaveAttribute(
      'href',
      '/catalog?type=MOVIE'
    );
    expect(screen.queryByRole('link', { name: /Ver todo Estrenos/ })).not.toBeInTheDocument();
  });
});
