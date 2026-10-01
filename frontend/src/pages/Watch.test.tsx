import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import Watch from './Watch';

const useContent = vi.fn();
const startPlayback = vi.fn();
const endSession = vi.fn();
const profileMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));

vi.mock('@lib/api', () => ({ useContent: (...args: unknown[]) => useContent(...args) }));

vi.mock('@hooks/useSelectedProfile', () => ({
  useSelectedProfile: () => profileMock.current,
}));

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ user: { access_token: 'token' } }),
}));

vi.mock('hls.js', () => ({ default: { isSupported: () => false } }));

vi.mock('../lib/api/playback', () => ({
  PLAYER_MESSAGES: {
    AUTHENTICATION_REQUIRED: 'Iniciá sesión para reproducir este título.',
    SUBSCRIPTION_REQUIRED: 'Necesitás una suscripción activa para ver este título.',
    CONTENT_NOT_READY: 'Este título todavía no tiene una versión para reproducir.',
    STREAM_LIMIT:
      'Tu plan no permite más reproducciones simultáneas. Cerrá otra y volvé a intentar.',
    UNAVAILABLE: 'No pudimos verificar tu plan en este momento. Probá de nuevo en unos minutos.',
    UNKNOWN: 'No pudimos iniciar la reproducción.',
  },
  startPlayback: (...args: unknown[]) => startPlayback(...args),
  heartbeat: vi.fn().mockResolvedValue(true),
  endSession: (...args: unknown[]) => endSession(...args),
}));

const CONTENT = {
  id: 'a2',
  type: 'SERIES',
  title: 'Arcane',
  releaseYear: 2021,
  synopsis: 'Piltover.',
};

function content(playback: Record<string, unknown>) {
  return { isLoading: false, data: { content: CONTENT, playback, degraded: [] } };
}

describe('Watch', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    profileMock.current = {
      profiles: [],
      selected: { id: 'p1', name: 'Demo' },
      isLoading: false,
    };
    endSession.mockResolvedValue(true);
    vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockReturnValue('maybe');
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
  });

  it('shows the paywall with a way to the plans', () => {
    useContent.mockReturnValue(content({ allowed: false, reason: 'SUBSCRIPTION_REQUIRED' }));

    renderWithProviders(<Watch />, { route: '/watch/a2' });

    expect(
      screen.getByText('Necesitás una suscripción activa para ver este título.')
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver planes' })).toHaveAttribute('href', '/plans');
  });

  it('asks for a session when the visitor is anonymous', () => {
    useContent.mockReturnValue(content({ allowed: false, reason: 'AUTHENTICATION_REQUIRED' }));

    renderWithProviders(<Watch />, { route: '/watch/a2' });

    expect(screen.getByText('Iniciá sesión para reproducir este título.')).toBeInTheDocument();
  });

  it('starts a session and plays when the account may watch', async () => {
    useContent.mockReturnValue(content({ allowed: true }));
    startPlayback.mockResolvedValue({
      ok: true,
      session: { id: 's1', streamPath: '/streams/s1/master.m3u8' },
    });
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));

    expect(startPlayback).toHaveBeenCalledWith({ profileId: 'p1', contentId: 'a2' });
    expect(await screen.findByText('Reproduciendo…')).toBeInTheDocument();
  });

  it('shows the mapped message when the session cannot start', async () => {
    useContent.mockReturnValue(content({ allowed: true }));
    startPlayback.mockResolvedValue({
      ok: false,
      code: 'SUBSCRIPTION_REQUIRED',
      message: 'Necesitás una suscripción activa para ver este título.',
    });
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));

    expect(
      await screen.findByText('Necesitás una suscripción activa para ver este título.')
    ).toBeInTheDocument();
  });

  it('waits with a skeleton while the title loads', () => {
    useContent.mockReturnValue({ isLoading: true });

    const { container } = renderWithProviders(<Watch />, { route: '/watch/a2' });

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });
});
