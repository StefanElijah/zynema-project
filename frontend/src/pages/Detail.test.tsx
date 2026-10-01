import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Detail from './Detail';

const useContent = vi.fn();
const authMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));
const watchlistMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));

vi.mock('@lib/api', () => ({ useContent: (...args: unknown[]) => useContent(...args) }));
vi.mock('react-oidc-context', () => ({ useAuth: () => authMock.current }));
vi.mock('@hooks/useSelectedProfile', () => ({
  useSelectedProfile: () => ({ profiles: [], selected: undefined, isLoading: false }),
}));
vi.mock('@hooks/useWatchlistActions', () => ({
  useWatchlistActions: () => watchlistMock.current,
}));

const CONTENT = {
  id: 'a1',
  type: 'MOVIE',
  title: 'Dune',
  releaseYear: 2021,
  runtimeMinutes: 155,
  synopsis: 'La casa Atreides viaja a Arrakis.',
  genres: [{ slug: 'sci-fi', name: 'Ciencia ficción' }],
  seasons: [{ seasonNumber: 1, episodeCount: 9 }],
  credits: [{ personName: 'Timothée Chalamet', role: 'ACTOR' }],
};

function detail(playback: Record<string, unknown>) {
  return { isLoading: false, data: { content: CONTENT, playback, degraded: [] } };
}

describe('Detail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMock.current = { signinRedirect: vi.fn() };
    watchlistMock.current = {
      canManage: false,
      add: vi.fn(),
      remove: vi.fn(),
      isPending: false,
      error: null,
    };
  });

  it('plays when the BFF says the account may watch', async () => {
    useContent.mockReturnValue(detail({ allowed: true }));
    const user = userEvent.setup();

    renderWithProviders(<Detail />, { route: '/title/dune' });

    expect(screen.getByRole('heading', { name: 'Dune', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('2 h 35 min')).toBeInTheDocument();
    expect(screen.getByText('Temporada 1')).toBeInTheDocument();
    expect(screen.getByText('Timothée Chalamet')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Reproducir/ }));
    expect(screen.getByTestId('location')).toHaveTextContent('/watch/a1');
  });

  it('asks an anonymous visitor to sign in', async () => {
    useContent.mockReturnValue(detail({ allowed: false, reason: 'AUTHENTICATION_REQUIRED' }));
    const user = userEvent.setup();

    renderWithProviders(<Detail />, { route: '/title/dune' });
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión para ver' }));

    expect(authMock.current.signinRedirect).toHaveBeenCalled();
  });

  it('sends a visitor without a plan to the pricing page', () => {
    useContent.mockReturnValue(detail({ allowed: false, reason: 'SUBSCRIPTION_REQUIRED' }));

    renderWithProviders(<Detail />, { route: '/title/dune' });

    expect(screen.getByRole('link', { name: 'Necesitás un plan' })).toHaveAttribute(
      'href',
      '/plans'
    );
  });

  it('admits when the plan could not be verified', () => {
    useContent.mockReturnValue(detail({ allowed: false, reason: 'UNAVAILABLE' }));

    renderWithProviders(<Detail />, { route: '/title/dune' });

    expect(
      screen.getByText(
        'No pudimos verificar tu plan en este momento. Probá de nuevo en unos minutos.'
      )
    ).toBeInTheDocument();
  });

  it('says the title does not exist on a 404', () => {
    useContent.mockReturnValue({
      isLoading: false,
      isError: true,
      error: { response: { status: 404 } },
    });

    renderWithProviders(<Detail />, { route: '/title/nope' });

    expect(screen.getByText('No encontramos ese título.')).toBeInTheDocument();
  });

  it('reports a generic failure that is not a 404', () => {
    useContent.mockReturnValue({
      isLoading: false,
      isError: true,
      error: { response: { status: 503 } },
    });

    renderWithProviders(<Detail />, { route: '/title/dune' });

    expect(screen.getByText('No pudimos cargar el título.')).toBeInTheDocument();
  });

  it('shows skeletons while the title loads', () => {
    useContent.mockReturnValue({ isLoading: true });

    const { container } = renderWithProviders(<Detail />, { route: '/title/dune' });

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('renders nothing when the answer carries no content', () => {
    useContent.mockReturnValue({ isLoading: false, data: { content: {} } });

    renderWithProviders(<Detail />, { route: '/title/dune' });

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('paints the backdrop, the rating badge and the season count', () => {
    useContent.mockReturnValue({
      isLoading: false,
      data: {
        content: {
          ...CONTENT,
          type: 'SERIES',
          backdropUrl: '/arcane-backdrop.jpg',
          maturityRating: '16',
        },
        playback: { allowed: true },
        degraded: [],
      },
    });

    const { container } = renderWithProviders(<Detail />, { route: '/title/arcane' });

    expect(container.querySelector('[style*="arcane-backdrop.jpg"]')).not.toBeNull();
    expect(screen.getByText('16')).toBeInTheDocument();
    expect(screen.getByText(/temporadas/)).toHaveTextContent('1 temporadas');
  });

  it('shows the watchlist error when the list cannot be updated', () => {
    useContent.mockReturnValue(detail({ allowed: true }));
    watchlistMock.current = {
      canManage: true,
      add: vi.fn(),
      remove: vi.fn(),
      isPending: false,
      error: { response: { data: { message: 'boom' } } },
    };

    renderWithProviders(<Detail />, { route: '/title/dune' });

    expect(screen.getByText('No pudimos actualizar tu lista. Probá de nuevo.')).toBeInTheDocument();
  });
});
