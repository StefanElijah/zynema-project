import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import Hls from 'hls.js';
import { useContent } from '@lib/api';
import { useSelectedProfile } from '@hooks/useSelectedProfile';
import { useProfileStore } from '@stores/useProfileStore';
import { startPlayback, heartbeat, endSession, PLAYER_MESSAGES } from '../lib/api/playback';
import PlayerControls from '../components/organisms/PlayerControls';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';

const REASON_MESSAGES: Record<string, string> = {
  AUTHENTICATION_REQUIRED: PLAYER_MESSAGES.AUTHENTICATION_REQUIRED,
  SUBSCRIPTION_REQUIRED: PLAYER_MESSAGES.SUBSCRIPTION_REQUIRED,
  UNAVAILABLE: PLAYER_MESSAGES.UNAVAILABLE,
};

type PlayerStatus = 'ready' | 'starting' | 'playing' | 'blocked' | 'error' | 'loading';

/**
 * The player.
 *
 * Read first: the title and the caller's playback context come from the typed
 * client (`useContent`). The session lifecycle stays in `lib/api/playback.ts`
 * because it is transport with a failure contract the player branches on
 * (paywall, not-ready, concurrency, unavailable), and its logic is unit-tested
 * there.
 *
 * The profile is the one selected globally in the navbar — watching as a
 * different profile must not need a second selection here.
 */
export default function Watch() {
  const { contentId } = useParams<{ contentId: string }>();
  const navigate = useNavigate();
  const auth = useAuth();
  const { profiles, selected } = useSelectedProfile();
  const selectProfile = useProfileStore((state) => state.select);

  const [status, setStatus] = useState<PlayerStatus>('loading');
  const [message, setMessage] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const sessionRef = useRef<{ id: string } | null>(null);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const resumeAtRef = useRef(0);
  // React detaches refs before passive-effect cleanups, so on unmount the video
  // element is already gone: the last position has to live in a ref.
  const positionRef = useRef(0);

  const { data, isLoading } = useContent(
    contentId ?? '',
    { profileId: selected?.id },
    { query: { enabled: Boolean(contentId) } }
  );

  const content = data?.content;
  const allowed = data?.playback?.allowed === true;

  useEffect(() => {
    if (isLoading) return;
    if (!allowed) {
      setStatus('blocked');
      const reason = data?.playback?.reason;
      setMessage((reason && REASON_MESSAGES[reason]) || PLAYER_MESSAGES.UNAVAILABLE);
    } else {
      setStatus('ready');
      setMessage(null);
    }
  }, [isLoading, allowed, data?.playback?.reason]);

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
    sessionRef.current = null;
    if (session) {
      await endSession(session.id, positionRef.current);
    }
  }, []);

  // The player's position, kept where the cleanup can still read it.
  useEffect(() => {
    const video = videoRef.current;
    if (status !== 'playing' || !video) return;
    const onTimeUpdate = () => {
      positionRef.current = Math.floor(video.currentTime || 0);
    };
    video.addEventListener('timeupdate', onTimeUpdate);
    return () => video.removeEventListener('timeupdate', onTimeUpdate);
  }, [status]);

  // Leaving the page ends the session: a stream nobody is watching is a
  // concurrent-stream slot somebody else cannot use.
  useEffect(
    () => () => {
      void stop();
    },
    [stop]
  );

  /** Returns false when the browser cannot play HLS: the caller must not claim it is playing. */
  const attachPlayer = (streamPath: string): boolean => {
    const video = videoRef.current;
    const token = auth.user?.access_token;
    if (!video) return false;

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
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (resumeAtRef.current > 0) {
          video.currentTime = resumeAtRef.current;
        }
        video.play().catch(() => {});
      });
      hls.on(Hls.Events.ERROR, (_event, error) => {
        if (error.fatal) {
          setStatus('error');
          setMessage('La reproducción se detuvo. Volvé a intentar.');
        }
      });
      hlsRef.current = hls;
      return true;
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari plays HLS natively, but cannot send a header for the manifest;
      // the token cookie flow is the future fix (see ADR-0024).
      video.src = streamPath;
      video.play().catch(() => {});
      return true;
    } else {
      setStatus('error');
      setMessage('Tu navegador no puede reproducir HLS.');
      return false;
    }
  };

  const play = async () => {
    if (!selected?.id || !content?.id) return;
    setStatus('starting');
    setMessage(null);
    resumeAtRef.current = data?.progress?.positionSeconds ?? 0;

    const result = await startPlayback({
      profileId: selected.id,
      contentId: content.id,
    });

    if (!result.ok) {
      setStatus('error');
      setMessage(result.message);
      return;
    }

    const sessionId = result.session.id;
    if (!sessionId) {
      setStatus('error');
      setMessage(PLAYER_MESSAGES.UNKNOWN);
      return;
    }

    sessionRef.current = { id: sessionId };
    if (!attachPlayer(result.session.streamPath ?? '')) {
      // The player already reported why; claiming "playing" would hide it.
      return;
    }
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
    return (
      <div className="custom-container py-16">
        <Skeleton className="aspect-video w-full max-w-4xl" />
      </div>
    );
  }

  const title = content?.title ?? contentId;
  const subtitle = content
    ? `${content.type === 'SERIES' ? 'Serie' : 'Película'} · ${content.releaseYear ?? ''}`
    : '';

  return (
    <div className="custom-container py-10">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="text-sm text-white/60 hover:text-white"
      >
        ← Volver
      </button>

      <div className="mt-6 grid gap-8 lg:grid-cols-[2fr_1fr]">
        <div className="overflow-hidden rounded-lg bg-black ring-1 ring-white/10">
          <video ref={videoRef} className="aspect-video w-full bg-black" playsInline />
          {status === 'playing' && <PlayerControls videoRef={videoRef} />}
        </div>

        <div>
          <h1 className="text-3xl font-semibold">{title}</h1>
          <p className="mt-1 text-sm text-white/50">{subtitle}</p>
          <p className="mt-4 text-sm text-white/70">{content?.synopsis}</p>

          {status === 'playing' && <p className="mt-6 text-sm text-emerald-400">Reproduciendo…</p>}

          {(status === 'ready' || status === 'starting') && (
            <div className="mt-6 space-y-4">
              {profiles.length > 1 && (
                <label className="block text-sm text-white/70">
                  Perfil
                  <select
                    value={selected?.id ?? ''}
                    onChange={(event) => selectProfile(event.target.value)}
                    className="mt-1 w-full rounded border border-white/20 bg-neutral-950 px-3 py-2 text-white"
                  >
                    {profiles.map((profile) => (
                      <option key={profile.id} value={profile.id}>
                        {profile.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <Button
                className="w-full"
                size="lg"
                onClick={() => void play()}
                disabled={status === 'starting' || !selected?.id}
              >
                {status === 'starting' ? 'Iniciando…' : '▶ Reproducir'}
              </Button>
              {!selected?.id && (
                <p className="text-xs text-white/50">
                  Elegí un perfil en la barra superior para reproducir.
                </p>
              )}
            </div>
          )}

          {(status === 'blocked' || status === 'error') && (
            <div className="mt-6 rounded border border-amber-500/40 bg-amber-500/10 p-4 text-sm text-amber-200">
              <p>{message}</p>
              {message === PLAYER_MESSAGES.SUBSCRIPTION_REQUIRED && (
                <Link to="/plans" className="mt-3 inline-block underline">
                  Ver planes
                </Link>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
