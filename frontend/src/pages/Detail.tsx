import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { useContent } from '@lib/api';
import { useSelectedProfile } from '@hooks/useSelectedProfile';
import { useWatchlistActions } from '@hooks/useWatchlistActions';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';

function formatRuntime(minutes?: number): string | null {
  if (!minutes) return null;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return hours > 0 ? `${hours} h ${rest} min` : `${rest} min`;
}

/**
 * The detail screen. It is a read: the BFF already joined the catalogue with
 * the caller's playback context (`playback.allowed`, `progress`), and this
 * page turns that answer into the right call to action instead of deciding
 * anything itself.
 */
export default function Detail() {
  const { idOrSlug } = useParams<{ idOrSlug: string }>();
  const navigate = useNavigate();
  const auth = useAuth();
  const watchlist = useWatchlistActions();
  const { selected } = useSelectedProfile();

  // The profile travels with the request: `playback` and `progress` are the
  // caller's context, and the BFF needs to know whose they are.
  const { data, isLoading, isError, error } = useContent(
    idOrSlug ?? '',
    { profileId: selected?.id },
    { query: { enabled: Boolean(idOrSlug) } }
  );

  if (isLoading) {
    return (
      <div className="custom-container py-16">
        <Skeleton className="h-12 w-1/3" />
        <Skeleton className="mt-4 h-5 w-1/4" />
        <Skeleton className="mt-8 h-28 w-full max-w-2xl" />
      </div>
    );
  }

  if (isError) {
    const status = (error as { response?: { status?: number } } | null)?.response?.status;
    return (
      <div className="py-24 text-center">
        <p className="text-red-400">
          {status === 404 ? 'No encontramos ese título.' : 'No pudimos cargar el título.'}
        </p>
        <Button variant="outline" className="mt-4" asChild>
          <Link to="/">Volver al inicio</Link>
        </Button>
      </div>
    );
  }

  const content = data?.content;
  if (!content?.id) {
    return null;
  }

  const allowed = data?.playback?.allowed === true;
  const reason = data?.playback?.reason;
  const runtime = formatRuntime(content.runtimeMinutes);

  const play = () => {
    if (allowed) {
      navigate(`/watch/${content.id}`);
      return;
    }
    if (reason === 'AUTHENTICATION_REQUIRED') {
      void auth.signinRedirect();
    }
  };

  const playLabel =
    reason === 'SUBSCRIPTION_REQUIRED'
      ? 'Necesitás un plan'
      : reason === 'AUTHENTICATION_REQUIRED'
        ? 'Iniciar sesión para ver'
        : '▶ Reproducir';

  return (
    <div>
      <div className="relative overflow-hidden">
        {content.backdropUrl && (
          <div
            className="absolute inset-0 -z-10 bg-cover bg-center opacity-30"
            style={{ backgroundImage: `url(${content.backdropUrl})` }}
          />
        )}
        <div className="absolute inset-0 -z-10 bg-gradient-to-t from-black via-black/70 to-transparent" />

        <div className="custom-container py-14">
          <h1 className="text-4xl font-bold md:text-5xl">{content.title}</h1>

          <div className="mt-3 flex flex-wrap items-center gap-3 text-sm text-white/60">
            {content.releaseYear && <span>{content.releaseYear}</span>}
            {content.maturityRating && (
              <span className="rounded border border-white/25 px-1.5 py-0.5 text-xs">
                {content.maturityRating}
              </span>
            )}
            {runtime && <span>{runtime}</span>}
            {content.type === 'SERIES' && (content.seasons?.length ?? 0) > 0 && (
              <span>{content.seasons?.length} temporadas</span>
            )}
            {content.averageRating != null && <span>★ {content.averageRating}/10</span>}
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            {(content.genres ?? []).map((genre) => (
              <span
                key={genre.slug ?? genre.name}
                className="rounded-full border border-white/15 px-3 py-0.5 text-xs text-white/70"
              >
                {genre.name}
              </span>
            ))}
          </div>

          {content.synopsis && <p className="mt-5 max-w-2xl text-white/80">{content.synopsis}</p>}

          <div className="mt-7 flex flex-wrap gap-3">
            {reason === 'SUBSCRIPTION_REQUIRED' ? (
              <Button size="lg" asChild>
                <Link to="/plans">{playLabel}</Link>
              </Button>
            ) : (
              <Button size="lg" onClick={play}>
                {playLabel}
              </Button>
            )}
            <Button
              size="lg"
              variant="secondary"
              disabled={!watchlist.canManage}
              title={watchlist.canManage ? undefined : 'Elegí un perfil para usar tu lista'}
              onClick={() => watchlist.add(content.id)}
            >
              ＋ Mi Lista
            </Button>
          </div>

          {!allowed && reason === 'UNAVAILABLE' && (
            <p className="mt-4 text-sm text-amber-300">
              No pudimos verificar tu plan en este momento. Probá de nuevo en unos minutos.
            </p>
          )}

          {watchlist.error && (
            <p className="mt-4 text-sm text-amber-300">
              No pudimos actualizar tu lista. Probá de nuevo.
            </p>
          )}
        </div>
      </div>

      <div className="custom-container pb-12">
        {(content.seasons?.length ?? 0) > 0 && (
          <section className="mt-8">
            <h2 className="mb-3 text-lg font-medium">Temporadas</h2>
            <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {(content.seasons ?? []).map((season) => (
                <li
                  key={season.seasonNumber}
                  className="flex items-center justify-between rounded border border-white/10 bg-white/5 px-4 py-2 text-sm"
                >
                  <span>Temporada {season.seasonNumber}</span>
                  <span className="text-white/50">{season.episodeCount ?? 0} episodios</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {(content.credits?.length ?? 0) > 0 && (
          <section className="mt-8">
            <h2 className="mb-3 text-lg font-medium">Reparto</h2>
            <p className="text-sm text-white/70">
              {(content.credits ?? [])
                .slice(0, 8)
                .map((credit) => credit.personName)
                .filter(Boolean)
                .join(', ')}
            </p>
          </section>
        )}
      </div>
    </div>
  );
}
