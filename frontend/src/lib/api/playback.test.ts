import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AxiosResponse } from 'axios';

vi.mock('./client', () => ({
  apiClient: {
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const { apiClient } = await import('./client');
const { startPlayback, heartbeat, endSession, describePlaybackFailure, PLAYER_MESSAGES } =
  await import('./playback');

const failure = (status: number, code?: string) => ({
  response: { status, data: code ? { details: { code } } : {} },
});

const responseWith = (data: unknown) => ({ data }) as AxiosResponse;

describe('playback client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('starts a session with the profile, the title and the device', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      responseWith({ id: 'session-1', streamPath: '/api/v1/playback/stream/session-1/master.m3u8' })
    );

    const result = await startPlayback({
      profileId: 'profile-1',
      contentId: 'arcane',
      episodeId: 'episode-1',
    });

    expect(apiClient.post).toHaveBeenCalledWith('/playback/sessions', {
      profileId: 'profile-1',
      contentId: 'arcane',
      episodeId: 'episode-1',
      device: 'web',
    });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.session.streamPath).toContain('/master.m3u8');
    }
  });

  it('turns a paywall into a code the UI can act on', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(failure(402, 'SUBSCRIPTION_REQUIRED'));

    const result = await startPlayback({ profileId: 'p', contentId: 'c' });

    expect(result).toEqual({
      ok: false,
      code: 'SUBSCRIPTION_REQUIRED',
      message: PLAYER_MESSAGES.SUBSCRIPTION_REQUIRED,
    });
  });

  it('tells the five player failures apart', () => {
    expect(describePlaybackFailure(failure(401)).code).toBe('AUTHENTICATION_REQUIRED');
    expect(describePlaybackFailure(failure(402, 'SUBSCRIPTION_REQUIRED')).code).toBe(
      'SUBSCRIPTION_REQUIRED'
    );
    expect(describePlaybackFailure(failure(409)).code).toBe('CONTENT_NOT_READY');
    expect(describePlaybackFailure(failure(422)).code).toBe('STREAM_LIMIT');
    expect(describePlaybackFailure(failure(503)).code).toBe('UNAVAILABLE');
    expect(describePlaybackFailure(new Error('boom')).code).toBe('UNKNOWN');
  });

  it('never fails the video because a heartbeat failed', async () => {
    vi.mocked(apiClient.put).mockRejectedValue(new Error('offline'));

    await expect(heartbeat('session-1', 42)).resolves.toBe(false);
    expect(apiClient.put).toHaveBeenCalledWith('/playback/sessions/session-1/position', {
      positionSeconds: 42,
    });
  });

  it('closes the session with the final position', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(responseWith({}));

    await expect(endSession('session-1', 120)).resolves.toBe(true);
    expect(apiClient.post).toHaveBeenCalledWith('/playback/sessions/session-1/end', {
      positionSeconds: 120,
    });
  });
});
