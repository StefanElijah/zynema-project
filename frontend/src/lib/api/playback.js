import { apiClient } from './client';

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
};

/** Maps an axios failure to the message the player shows. */
export function describePlaybackFailure(error) {
  const status = error?.response?.status;
  const code = error?.response?.data?.details?.code;

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

export async function startPlayback({ profileId, contentId, episodeId = null, device = 'web' }) {
  try {
    const { data } = await apiClient.post('/playback/sessions', {
      profileId,
      contentId,
      episodeId,
      device,
    });
    return { ok: true, session: data };
  } catch (error) {
    return { ok: false, ...describePlaybackFailure(error) };
  }
}

/**
 * Best effort on purpose: a lost heartbeat must not stop the video, and the
 * next one carries the position anyway (playback-service has the same rule).
 */
export async function heartbeat(sessionId, positionSeconds) {
  try {
    await apiClient.put(`/playback/sessions/${sessionId}/position`, { positionSeconds });
    return true;
  } catch {
    return false;
  }
}

export async function endSession(sessionId, positionSeconds) {
  try {
    await apiClient.post(`/playback/sessions/${sessionId}/end`, { positionSeconds });
    return true;
  } catch {
    return false;
  }
}
