import { useRef } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PlayerControls from './PlayerControls';

/**
 * jsdom does not implement media playback: the element's state is defined per
 * test and the play/pause calls are spies, so the component's own logic (which
 * reads events and writes properties) is what gets exercised.
 */
interface MediaState {
  currentTime: number;
  duration: number;
  volume: number;
  muted: boolean;
  paused: boolean;
}

function Harness({ state }: { state: MediaState }) {
  const videoRef = useRef<HTMLVideoElement>(null);

  return (
    <>
      <video
        ref={(element) => {
          videoRef.current = element;
          if (element) {
            installMedia(element, state);
          }
        }}
        data-testid="video"
      />
      <PlayerControls videoRef={videoRef} />
    </>
  );
}

function installMedia(video: HTMLVideoElement, state: MediaState) {
  Object.defineProperties(video, {
    currentTime: {
      get: () => state.currentTime,
      set: (value: number) => {
        state.currentTime = value;
      },
      configurable: true,
    },
    duration: {
      get: () => state.duration,
      set: (value: number) => {
        state.duration = value;
      },
      configurable: true,
    },
    volume: {
      get: () => state.volume,
      set: (value: number) => {
        state.volume = value;
      },
      configurable: true,
    },
    muted: {
      get: () => state.muted,
      set: (value: boolean) => {
        state.muted = value;
      },
      configurable: true,
    },
    paused: { get: () => state.paused, configurable: true },
  });
  vi.spyOn(video, 'play').mockImplementation(async () => {
    state.paused = false;
  });
  vi.spyOn(video, 'pause').mockImplementation(() => {
    state.paused = true;
  });
}

describe('PlayerControls', () => {
  let state: MediaState;

  beforeEach(() => {
    state = { currentTime: 0, duration: 0, volume: 1, muted: false, paused: true };
    Element.prototype.requestFullscreen = vi.fn().mockResolvedValue(undefined);
    document.exitFullscreen = vi.fn().mockResolvedValue(undefined);
  });

  it('plays and pauses through the video element and its events', async () => {
    const user = userEvent.setup();
    render(<Harness state={state} />);
    const video = screen.getByTestId('video') as HTMLVideoElement;

    await user.click(screen.getByRole('button', { name: 'Reproducir' }));
    expect(video.play).toHaveBeenCalled();

    fireEvent(video, new Event('play'));
    expect(screen.getByRole('button', { name: 'Pausar' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Pausar' }));
    expect(video.pause).toHaveBeenCalled();
    fireEvent(video, new Event('pause'));
    expect(screen.getByRole('button', { name: 'Reproducir' })).toBeInTheDocument();
  });

  it('seeks with the skip buttons and the position slider', async () => {
    const user = userEvent.setup();
    render(<Harness state={state} />);
    const video = screen.getByTestId('video') as HTMLVideoElement;

    state.duration = 120;
    fireEvent(video, new Event('durationchange'));
    state.currentTime = 30;

    await user.click(screen.getByRole('button', { name: 'Retroceder 10 segundos' }));
    expect(video.currentTime).toBe(20);

    await user.click(screen.getByRole('button', { name: 'Avanzar 10 segundos' }));
    expect(video.currentTime).toBe(30);

    fireEvent.change(screen.getByLabelText('Posición'), { target: { value: '60' } });
    expect(video.currentTime).toBe(60);
  });

  it('shows the position and duration as clock time', () => {
    render(<Harness state={state} />);
    const video = screen.getByTestId('video') as HTMLVideoElement;

    state.duration = 120;
    fireEvent(video, new Event('durationchange'));
    state.currentTime = 65;
    fireEvent(video, new Event('timeupdate'));

    expect(screen.getByText('1:05')).toBeInTheDocument();
    expect(screen.getByText('2:00')).toBeInTheDocument();
  });

  it('mutes and changes the volume', async () => {
    const user = userEvent.setup();
    render(<Harness state={state} />);
    const video = screen.getByTestId('video') as HTMLVideoElement;

    await user.click(screen.getByRole('button', { name: 'Silenciar' }));
    expect(video.muted).toBe(true);

    // The label follows the element's own event, like a browser would fire it.
    fireEvent(video, new Event('volumechange'));
    expect(screen.getByRole('button', { name: 'Activar sonido' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Volumen'), { target: { value: '0.5' } });
    expect(video.volume).toBe(0.5);
  });

  it('asks the container for fullscreen', async () => {
    const user = userEvent.setup();
    render(<Harness state={state} />);

    await user.click(screen.getByRole('button', { name: 'Pantalla completa' }));
    expect(Element.prototype.requestFullscreen).toHaveBeenCalled();
  });
});
