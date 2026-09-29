import { AxiosError, AxiosResponse } from 'axios';
import { apiClient } from './client';
// The wire shape of a session is the generated contract; this module owns the
// failure mapping, not the types.
import type { Session as PlaybackSession } from '@zynema/api-contracts';

/**
 * Playback API calls for the player (Fase 6).
 *
 * Failures are translated into a code the UI can branch on, because each one
 * has a different answer: sign in, subscribe, wait for the rendition, close
 * another stream, or retry later. The player should never show a bare
 * "Request failed with status code 402".
 */
export const PLAYER_MESSAGES = {
  AUTHENTICATION_REQUIRED: 'Iniciá sesión para reproducir este título.',
  SUBSCRIPTION_REQUIRED: 'Necesitás una suscripción activa para ver este título.',
  CONTENT_NOT_READY: 'Este título todavía no tiene una versión para reproducir.',
  STREAM_LIMIT: 'Tu plan no permite más reproducciones simultáneas. Cerrá otra y volvé a intentar.',
  UNAVAILABLE: 'No pudimos verificar tu plan en este momento. Probá de nuevo en unos minutos.',
  UNKNOWN: 'No pudimos iniciar la reproducción.',
} as const;

export type PlaybackFailureCode = keyof typeof PLAYER_MESSAGES;

export interface PlaybackFailure {
  code: PlaybackFailureCode;
  message: string;
}

export type PlaybackResult =
  { ok: true; session: PlaybackSession } | ({ ok: false } & PlaybackFailure);

interface ApiErrorEnvelope {
  details?: { code?: string } | null;
}

/** Maps an axios failure to the message the player shows. */
export function describePlaybackFailure(error: unknown): PlaybackFailure {
  const axiosError = error as AxiosError<ApiErrorEnvelope>;
  const status = axiosError?.response?.status;
  const code = axiosError?.response?.data?.details?.code;

  if (code === 'SUBSCRIPTION_REQUIRED' || status === 402) {
    return { code: 'SUBSCRIPTION_REQUIRED', message: PLAYER_MESSAGES.SUBSCRIPTION_REQUIRED };
  }
  if (status === 401) {
    return { code: 'AUTHENTICATION_REQUIRED', message: PLAYER_MESSAGES.AUTHENTICATION_REQUIRED };
  }
  if (status === 409) {
    return { code: 'CONTENT_NOT_READY', message: PLAYER_MESSAGES.CONTENT_NOT_READY };
  }
  if (status === 422) {
    return { code: 'STREAM_LIMIT', message: PLAYER_MESSAGES.STREAM_LIMIT };
  }
  if (status === 503) {
    return { code: 'UNAVAILABLE', message: PLAYER_MESSAGES.UNAVAILABLE };
  }
  return { code: 'UNKNOWN', message: PLAYER_MESSAGES.UNKNOWN };
}

export interface StartPlaybackRequest {
  profileId: string;
  contentId: string;
  episodeId?: string | null;
  device?: string;
}

export async function startPlayback({
  profileId,
  contentId,
  episodeId = null,
  device = 'web',
}: StartPlaybackRequest): Promise<PlaybackResult> {
  try {
    const response: AxiosResponse<PlaybackSession> = await apiClient.post('/playback/sessions', {
      profileId,
      contentId,
      episodeId,
      device,
    });
    return { ok: true, session: response.data };
  } catch (error) {
    return { ok: false, ...describePlaybackFailure(error) };
  }
}

/**
 * Best effort on purpose: a lost heartbeat must not stop the video, and the
 * next one carries the position anyway (playback-service has the same rule).
 */
export async function heartbeat(sessionId: string, positionSeconds: number): Promise<boolean> {
  try {
    await apiClient.put(`/playback/sessions/${sessionId}/position`, { positionSeconds });
    return true;
  } catch {
    return false;
  }
}

export async function endSession(sessionId: string, positionSeconds: number): Promise<boolean> {
  try {
    await apiClient.post(`/playback/sessions/${sessionId}/end`, { positionSeconds });
    return true;
  } catch {
    return false;
  }
}
