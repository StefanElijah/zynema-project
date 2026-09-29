import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import Hls from 'hls.js';
import { apiClient } from '../lib/api/client';
import { startPlayback, heartbeat, endSession, PLAYER_MESSAGES } from '../lib/api/playback';
import type { AccountView, ContentView, PlaybackSession, UserProfile } from '../lib/api/types';

const REASON_MESSAGES: Record<string, string> = {
  AUTHENTICATION_REQUIRED: PLAYER_MESSAGES.AUTHENTICATION_REQUIRED,
  SUBSCRIPTION_REQUIRED: PLAYER_MESSAGES.SUBSCRIPTION_REQUIRED,
  UNAVAILABLE: PLAYER_MESSAGES.UNAVAILABLE,
};

type PlayerStatus = 'loading' | 'ready' | 'starting' | 'playing' | 'blocked' | 'error';

/**
 * The player (Fase 6).
 *
 * Screen flow: read the title and the account from the BFF (one call each, no
 * orchestration here), then a click starts a playback session and attaches
 * HLS. The click is not decoration: browsers only allow playback from a user
 * gesture.
 *
 * The contract with playback-service: the session carries `streamPath`, hls.js
 * sends the token for the manifests, and the segments are already signed
 * (ADR-0024), so nothing in this component formats a video URL.
 */
export default function Watch() {
  const { contentId } = useParams<{ contentId: string }>();
  const auth = useAuth();

  const [content, setContent] = useState<ContentView | null>(null);
  const [profiles, setProfiles] = useState<UserProfile[]>([]);
  const [profileId, setProfileId] = useState<string | null>(null);
  const [status, setStatus] = useState<PlayerStatus>('loading');
  const [message, setMessage] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const sessionRef = useRef<PlaybackSession | null>(null);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [detail, account] = await Promise.all([
          apiClient.get<ContentView>(`/web/catalog/${contentId}`),
          apiClient.get<AccountView>('/web/account'),
        ]);
        if (cancelled) return;

        setContent(detail.data);
        setProfiles(account.data.user?.profiles ?? []);
        setProfileId(account.data.user?.profiles?.[0]?.id ?? null);

        if (detail.data.playback?.allowed) {
          setStatus('ready');
        } else {
          setStatus('blocked');
          const reason = detail.data.playback?.reason;
          setMessage((reason && REASON_MESSAGES[reason]) || PLAYER_MESSAGES.UNAVAILABLE);
        }
      } catch (error) {
        if (cancelled) return;
        setStatus('error');
        setMessage(
          error &&
            typeof error === 'object' &&
            'response' in error &&
            (error as { response?: { status?: number } }).response?.status === 404
            ? 'No encontramos ese título.'
            : PLAYER_MESSAGES.UNKNOWN
        );
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [contentId]);

  const stop = useCallback(async () => {
    if (heartbeatRef.current) {
      clearInterval(heartbeatRef.current);
      heartbeatRef.current = null;
    }
    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }
    const session = sessionRef.current;
    const video = videoRef.current;
    sessionRef.current = null;
    if (session && video) {
      await endSession(session.id, Math.floor(video.currentTime || 0));
    }
  }, []);

  // Leaving the page ends the session: a stream nobody is watching is a
  // concurrent-stream slot somebody else cannot use.
  useEffect(
    () => () => {
      void stop();
    },
    [stop]
  );

  const attachPlayer = (streamPath: string) => {
    const video = videoRef.current;
    const token = auth.user?.access_token;
    if (!video) return;

    if (Hls.isSupported()) {
      const hls = new Hls({
        // Manifests are private; segments are already signed and are fetched
        // from the storage edge without credentials.
        xhrSetup: (xhr) => {
          if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
        },
      });
      hls.loadSource(streamPath);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => video.play().catch(() => {}));
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal) {
          setStatus('error');
          setMessage('La reproducción se detuvo. Volvé a intentar.');
        }
      });
      hlsRef.current = hls;
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari plays HLS natively, but cannot send a header for the manifest;
      // the token cookie flow is the future fix (see ADR-0024).
      video.src = streamPath;
      video.play().catch(() => {});
    } else {
      setStatus('error');
      setMessage('Tu navegador no puede reproducir HLS.');
    }
  };

  const play = async () => {
    if (!profileId || !content) return;
    setStatus('starting');
    setMessage(null);

    const result = await startPlayback({
      profileId,
      // The route may carry a slug; playback wants the id the catalogue uses.
      contentId: content.content?.id ?? contentId ?? '',
    });

    if (!result.ok) {
      setStatus('error');
      setMessage(result.message);
      return;
    }

    sessionRef.current = result.session;
    attachPlayer(result.session.streamPath);
    setStatus('playing');
    heartbeatRef.current = setInterval(() => {
      const video = videoRef.current;
      if (video && sessionRef.current) {
        void heartbeat(sessionRef.current.id, Math.floor(video.currentTime || 0));
      }
    }, 15000);
  };

  // ────────────────────────────── render ────────────────────────────────

  if (status === 'loading') {
    return <div className="py-24 text-center text-white/70">Cargando el título…</div>;
  }

  const title = content?.content?.title ?? contentId;
  const subtitle =
    content?.content?.type === 'SERIES'
      ? `Serie · ${content?.content?.releaseYear ?? ''}`
      : `Película · ${content?.content?.releaseYear ?? ''}`;

  return (
    <div className="custom-container py-10">
      <Link to="/" className="text-sm text-white/60 hover:text-white">
        ← Volver
      </Link>

      <div className="mt-6 grid gap-8 lg:grid-cols-[2fr_1fr]">
        <div className="overflow-hidden rounded-lg bg-black ring-1 ring-white/10">
          <video
            ref={videoRef}
            className="aspect-video w-full bg-black"
            controls={status === 'playing'}
            playsInline
          />
        </div>

        <div>
          <h1 className="text-3xl font-semibold">{title}</h1>
          <p className="mt-1 text-sm text-white/50">{subtitle}</p>
          <p className="mt-4 text-sm text-white/70">{content?.content?.synopsis}</p>

          {status === 'playing' && <p className="mt-6 text-sm text-emerald-400">Reproduciendo…</p>}

          {(status === 'ready' || status === 'starting') && (
            <div className="mt-6 space-y-4">
              {profiles.length > 1 && (
                <label className="block text-sm text-white/70">
                  Perfil
                  <select
                    value={profileId ?? ''}
                    onChange={(event) => setProfileId(event.target.value)}
                    className="mt-1 w-full rounded bg-white/10 px-3 py-2 text-white"
                  >
                    {profiles.map((profile) => (
                      <option key={profile.id} value={profile.id}>
                        {profile.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <button
                type="button"
                onClick={play}
                disabled={status === 'starting' || !profileId}
                className="w-full rounded bg-red-600 px-6 py-3 font-medium text-white hover:bg-red-500 disabled:opacity-50"
              >
                {status === 'starting' ? 'Iniciando…' : '▶ Reproducir'}
              </button>
            </div>
          )}

          {(status === 'blocked' || status === 'error') && (
            <div className="mt-6 rounded border border-amber-500/40 bg-amber-500/10 p-4 text-sm text-amber-200">
              <p>{message}</p>
              {message === PLAYER_MESSAGES.SUBSCRIPTION_REQUIRED && (
                <Link to="/account" className="mt-3 inline-block underline">
                  Ver mi cuenta y suscripción
                </Link>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
