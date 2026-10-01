import { act, fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useProfileStore } from '@stores/useProfileStore';
import { renderWithProviders } from '../test/renderWithProviders';
import Watch from './Watch';

const useContent = vi.fn();
const startPlayback = vi.fn();
const heartbeat = vi.fn();
const endSession = vi.fn();
const profileMock = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));

const hlsMock = vi.hoisted(() => ({
  supported: true,
  handlers: {} as Record<string, (...args: unknown[]) => void>,
  config: undefined as
    | { xhrSetup?: (xhr: { setRequestHeader: (name: string, value: string) => void }) => void }
    | undefined,
  instance: undefined as { destroy: () => void } | undefined,
}));

vi.mock('@lib/api', () => ({ useContent: (...args: unknown[]) => useContent(...args) }));

vi.mock('@hooks/useSelectedProfile', () => ({
  useSelectedProfile: () => profileMock.current,
}));

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ user: { access_token: 'token' } }),
}));

vi.mock('hls.js', () => {
  class HlsMock {
    static isSupported() {
      return hlsMock.supported;
    }
    static Events = { MANIFEST_PARSED: 'manifestParsed', ERROR: 'error' };

    loadSource = vi.fn();
    attachMedia = vi.fn();
    destroy = vi.fn();
    on = (event: string, handler: (...args: unknown[]) => void) => {
      hlsMock.handlers[event] = handler;
    };

    constructor(config: typeof hlsMock.config) {
      hlsMock.config = config;
      hlsMock.instance = this;
    }
  }
  return { default: HlsMock };
});

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
  heartbeat: (...args: unknown[]) => heartbeat(...args),
  endSession: (...args: unknown[]) => endSession(...args),
}));

const CONTENT = {
  id: 'a2',
  type: 'SERIES',
  title: 'Arcane',
  releaseYear: 2021,
  synopsis: 'Piltover.',
};

function content(playback: Record<string, unknown>, extra: Record<string, unknown> = {}) {
  return { isLoading: false, data: { content: CONTENT, playback, degraded: [], ...extra } };
}

describe('Watch', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useProfileStore.getState().clear();
    profileMock.current = {
      profiles: [],
      selected: { id: 'p1', name: 'Demo' },
      isLoading: false,
    };
    hlsMock.supported = true;
    hlsMock.handlers = {};
    hlsMock.config = undefined;
    hlsMock.instance = undefined;
    endSession.mockResolvedValue(true);
    heartbeat.mockResolvedValue(true);
    vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockReturnValue('maybe');
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
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

  it('admits when the session came back without an id', async () => {
    useContent.mockReturnValue(content({ allowed: true }));
    startPlayback.mockResolvedValue({ ok: true, session: { streamPath: '/x' } });
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));

    expect(await screen.findByText('No pudimos iniciar la reproducción.')).toBeInTheDocument();
  });

  it('waits with a skeleton while the title loads', () => {
    useContent.mockReturnValue({ isLoading: true });

    const { container } = renderWithProviders(<Watch />, { route: '/watch/a2' });

    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
  });

  it('asks for a profile when none is selected', () => {
    useContent.mockReturnValue(content({ allowed: true }));
    profileMock.current = { profiles: [], selected: undefined, isLoading: false };

    renderWithProviders(<Watch />, { route: '/watch/a2' });

    expect(screen.getByRole('button', { name: '▶ Reproducir' })).toBeDisabled();
    expect(
      screen.getByText('Elegí un perfil en la barra superior para reproducir.')
    ).toBeInTheDocument();
  });

  it('switches the watching profile from the selector', async () => {
    useContent.mockReturnValue(content({ allowed: true }));
    profileMock.current = {
      profiles: [
        { id: 'p1', name: 'Demo' },
        { id: 'p2', name: 'Kids' },
      ],
      selected: { id: 'p1', name: 'Demo' },
      isLoading: false,
    };
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.selectOptions(screen.getByLabelText('Perfil'), 'p2');

    expect(useProfileStore.getState().selectedProfileId).toBe('p2');
  });

  it('resumes from the recorded position through the HLS path', async () => {
    useContent.mockReturnValue(
      content({ allowed: true }, { progress: { positionSeconds: 42, durationSeconds: 2400 } })
    );
    startPlayback.mockResolvedValue({
      ok: true,
      session: { id: 's1', streamPath: '/streams/s1/master.m3u8' },
    });
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));
    await screen.findByText('Reproduciendo…');

    const video = document.querySelector('video') as HTMLVideoElement;
    act(() => {
      hlsMock.handlers['manifestParsed']?.();
    });

    expect(video.currentTime).toBe(42);
    expect(video.play).toHaveBeenCalled();
  });

  it('sends the token on the manifest requests', async () => {
    useContent.mockReturnValue(content({ allowed: true }));
    startPlayback.mockResolvedValue({
      ok: true,
      session: { id: 's1', streamPath: '/streams/s1/master.m3u8' },
    });
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));
    await screen.findByText('Reproduciendo…');

    const setRequestHeader = vi.fn();
    hlsMock.config?.xhrSetup?.({ setRequestHeader });

    expect(setRequestHeader).toHaveBeenCalledWith('Authorization', 'Bearer token');
  });

  it('reports a fatal playback error from HLS', async () => {
    useContent.mockReturnValue(content({ allowed: true }));
    startPlayback.mockResolvedValue({
      ok: true,
      session: { id: 's1', streamPath: '/streams/s1/master.m3u8' },
    });
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));
    await screen.findByText('Reproduciendo…');

    act(() => {
      hlsMock.handlers['error']?.({}, { fatal: true });
    });

    expect(screen.getByText('La reproducción se detuvo. Volvé a intentar.')).toBeInTheDocument();
  });

  it('closes the session when leaving the page', async () => {
    useContent.mockReturnValue(content({ allowed: true }));
    startPlayback.mockResolvedValue({
      ok: true,
      session: { id: 's1', streamPath: '/streams/s1/master.m3u8' },
    });
    const user = userEvent.setup();

    const { unmount } = renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));
    await screen.findByText('Reproduciendo…');

    // The last position the element reported is what the session closes with.
    const video = document.querySelector('video') as HTMLVideoElement;
    let currentTime = 30;
    Object.defineProperty(video, 'currentTime', {
      get: () => currentTime,
      set: (value: number) => {
        currentTime = value;
      },
      configurable: true,
    });
    act(() => {
      fireEvent(video, new Event('timeupdate'));
    });

    unmount();

    expect(hlsMock.instance?.destroy).toHaveBeenCalled();
    expect(endSession).toHaveBeenCalledWith('s1', 30);
  });

  it('does nothing when the answer carries no content', async () => {
    useContent.mockReturnValue({ isLoading: false, data: { playback: { allowed: true } } });
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));

    expect(startPlayback).not.toHaveBeenCalled();
  });

  it('says when the browser cannot play HLS at all', async () => {
    hlsMock.supported = false;
    vi.mocked(HTMLMediaElement.prototype.canPlayType).mockReturnValue('');
    useContent.mockReturnValue(content({ allowed: true }));
    startPlayback.mockResolvedValue({
      ok: true,
      session: { id: 's1', streamPath: '/streams/s1/master.m3u8' },
    });
    const user = userEvent.setup();

    renderWithProviders(<Watch />, { route: '/watch/a2' });
    await user.click(screen.getByRole('button', { name: '▶ Reproducir' }));

    expect(await screen.findByText('Tu navegador no puede reproducir HLS.')).toBeInTheDocument();
  });

  it('heartbeats the position every fifteen seconds', async () => {
    vi.useFakeTimers();
    useContent.mockReturnValue(content({ allowed: true }));
    startPlayback.mockResolvedValue({
      ok: true,
      session: { id: 's1', streamPath: '/streams/s1/master.m3u8' },
    });

    renderWithProviders(<Watch />, { route: '/watch/a2' });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '▶ Reproducir' }));
    });
    expect(screen.getByText('Reproduciendo…')).toBeInTheDocument();

    await act(async () => {
      vi.advanceTimersByTime(15000);
    });

    expect(heartbeat).toHaveBeenCalledWith('s1', 0);
  });
});
