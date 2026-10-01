import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Search from './Search';

const useSearch = vi.fn();

vi.mock('@lib/api', () => ({
  useSearch: (params: unknown, options: unknown) => useSearch(params, options),
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

const RESULTS = {
  content: [{ id: 'a1', title: 'Dune', releaseYear: 2021 }],
  page: 0,
  totalPages: 1,
  last: true,
};

describe('Search', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useSearch.mockReturnValue({ isLoading: false, data: RESULTS });
  });

  it('waits for a query', () => {
    renderWithProviders(<Search />, { route: '/search' });

    expect(screen.getByText('Escribí algo para buscar en el catálogo.')).toBeInTheDocument();
    // The query is disabled until there is something to search for.
    expect(useSearch).toHaveBeenCalledWith(
      expect.objectContaining({ q: '' }),
      expect.objectContaining({ query: expect.objectContaining({ enabled: false }) })
    );
  });

  it('commits the query to the URL and shows the results', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Search />, { route: '/search' });

    await user.type(screen.getByRole('searchbox', { name: 'Buscar en el catálogo' }), 'dune');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(screen.getByTestId('location')).toHaveTextContent('q=dune');
    expect(screen.getByRole('heading', { name: 'Resultados para “dune”' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Dune' })).toBeInTheDocument();
  });

  it('says so when there are no results', () => {
    useSearch.mockReturnValue({
      isLoading: false,
      data: { ...RESULTS, content: [], totalPages: 0 },
    });

    renderWithProviders(<Search />, { route: '/search?q=zzz' });

    expect(screen.getByText('Sin resultados para “zzz”.')).toBeInTheDocument();
  });

  it('shows skeletons while the query is in flight', () => {
    useSearch.mockReturnValue({ isLoading: true });

    const { container } = renderWithProviders(<Search />, { route: '/search?q=dune' });

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('offers a retry when the search fails', async () => {
    const refetch = vi.fn();
    useSearch.mockReturnValue({ isLoading: false, isError: true, refetch });
    const user = userEvent.setup();

    renderWithProviders(<Search />, { route: '/search?q=dune' });
    expect(screen.getByText('La búsqueda falló.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(refetch).toHaveBeenCalled();
  });

  it('paginates the results through the URL', async () => {
    useSearch.mockReturnValue({
      isLoading: false,
      data: { ...RESULTS, totalPages: 2, last: false },
    });
    const user = userEvent.setup();

    renderWithProviders(<Search />, { route: '/search?q=dune' });

    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(screen.getByTestId('location')).toHaveTextContent('page=1');
  });

  it('walks back to the previous page', async () => {
    useSearch.mockReturnValue({
      isLoading: false,
      data: { ...RESULTS, page: 1, totalPages: 2, first: false, last: true },
    });
    const user = userEvent.setup();

    renderWithProviders(<Search />, { route: '/search?q=dune&page=1' });

    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Anterior' }));
    expect(screen.getByTestId('location')).toHaveTextContent('page=0');
  });
});
