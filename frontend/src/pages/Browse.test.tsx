import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Browse from './Browse';

const useBrowse = vi.fn();

vi.mock('@lib/api', () => ({ useBrowse: (params: unknown) => useBrowse(params) }));
vi.mock('@hooks/useWatchlistActions', () => ({
  useWatchlistActions: () => ({
    canManage: false,
    add: vi.fn(),
    remove: vi.fn(),
    isPending: false,
    error: null,
  }),
}));

const PAGE = {
  content: [{ id: 'a1', title: 'Dune', releaseYear: 2021 }],
  page: 0,
  totalPages: 3,
  first: true,
  last: false,
};

describe('Browse', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useBrowse.mockReturnValue({ isLoading: false, data: PAGE });
  });

  it('reads the filters from the URL and asks the catalogue for them', () => {
    renderWithProviders(<Browse />, { route: '/catalog?type=MOVIE' });

    expect(screen.getByRole('heading', { name: 'Películas', level: 1 })).toBeInTheDocument();
    expect(useBrowse).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'MOVIE', sort: 'popularity,desc', page: 0, size: 24 })
    );
    expect(screen.getByRole('img', { name: 'Dune' })).toBeInTheDocument();
  });

  it('changes the sort in the URL', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Browse />, { route: '/catalog?type=SERIES' });

    await user.selectOptions(screen.getByLabelText('Ordenar por'), 'releaseYear,desc');

    expect(screen.getByTestId('location')).toHaveTextContent('sort=releaseYear%2Cdesc');
    expect(useBrowse).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'releaseYear,desc' })
    );
  });

  it('paginates', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Browse />, { route: '/catalog?type=MOVIE' });

    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(screen.getByTestId('location')).toHaveTextContent('page=1');
  });

  it('says so when nothing matches', () => {
    useBrowse.mockReturnValue({ isLoading: false, data: { ...PAGE, content: [], totalPages: 0 } });

    renderWithProviders(<Browse />, { route: '/catalog?type=MOVIE' });

    expect(screen.getByText('No hay títulos con estos filtros.')).toBeInTheDocument();
  });

  it('offers a retry when the catalogue is down', async () => {
    const refetch = vi.fn();
    useBrowse.mockReturnValue({ isLoading: false, isError: true, refetch });
    const user = userEvent.setup();

    renderWithProviders(<Browse />, { route: '/catalog?type=MOVIE' });
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(refetch).toHaveBeenCalled();
  });

  it('shows skeletons while the page loads', () => {
    useBrowse.mockReturnValue({ isLoading: true });

    const { container } = renderWithProviders(<Browse />, { route: '/catalog?type=MOVIE' });

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('returns to the first page when the filter changes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Browse />, { route: '/catalog?type=MOVIE&page=2' });

    await user.selectOptions(screen.getByLabelText('Ordenar por'), 'averageRating,desc');

    const location = screen.getByTestId('location');
    expect(location).toHaveTextContent('sort=averageRating%2Cdesc');
    expect(location).not.toHaveTextContent('page=');
  });
});
