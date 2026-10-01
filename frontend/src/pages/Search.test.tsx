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
});
