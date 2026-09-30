import { useNavigate } from 'react-router-dom';
import { useProfileHome, type TitleCard } from '@lib/api';
import { useSelectedProfile } from '@hooks/useSelectedProfile';
import { useWatchlistActions } from '@hooks/useWatchlistActions';
import ContentCard from '../components/molecules/ContentCard';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';

function CardGrid({
  items,
  onRemove,
}: {
  items: TitleCard[];
  onRemove?: (contentId?: string) => void;
}) {
  const navigate = useNavigate();

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
      {items.map((item) => (
        <div key={item.id} className="mb-3">
          <ContentCard item={item} onPlay={() => navigate(`/watch/${item.id}`)} />
          {onRemove && (
            <Button
              variant="ghost"
              size="sm"
              className="mt-1 w-full text-white/60"
              onClick={() => onRemove(item.id)}
            >
              Quitar
            </Button>
          )}
        </div>
      ))}
    </div>
  );
}

/**
 * The selected profile's own rails: what it is watching and what it saved.
 * Both are the same data the profile home carries — no extra endpoints, no
 * client-side merging.
 */
export default function MyList() {
  const { selected, isLoading: profilesLoading } = useSelectedProfile();
  const watchlist = useWatchlistActions();

  const { data, isLoading } = useProfileHome(selected?.id ?? '', {
    query: { enabled: Boolean(selected?.id) },
  });

  const continueWatching = (data?.continueWatching ?? [])
    .map((entry) => entry.content)
    .filter((card): card is TitleCard => Boolean(card?.id));
  const myList = (data?.myList ?? [])
    .map((entry) => entry.content)
    .filter((card): card is TitleCard => Boolean(card?.id));

  if (profilesLoading || isLoading) {
    return (
      <div className="custom-container py-10">
        <Skeleton className="h-8 w-40" />
        <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="aspect-[2/3] w-full" />
          ))}
        </div>
      </div>
    );
  }

  if (!selected) {
    return (
      <p className="py-24 text-center text-white/60">
        Elegí un perfil en la barra superior para ver tu lista.
      </p>
    );
  }

  return (
    <div className="custom-container py-10">
      <h1 className="text-3xl font-semibold">Mi Lista</h1>
      <p className="mt-1 text-sm text-white/60">Perfil: {selected.name}</p>

      {(data?.degraded?.length ?? 0) > 0 && (
        <p className="mt-4 rounded border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-200">
          Algunas secciones no están disponibles ahora: {data?.degraded?.join(', ')}.
        </p>
      )}

      <section className="mt-10">
        <h2 className="mb-4 text-lg font-medium">Seguir viendo</h2>
        {continueWatching.length > 0 ? (
          <CardGrid items={continueWatching} />
        ) : (
          <p className="text-sm text-white/50">Todavía no empezaste nada.</p>
        )}
      </section>

      <section className="mt-12">
        <h2 className="mb-4 text-lg font-medium">Guardado</h2>
        {myList.length > 0 ? (
          <CardGrid items={myList} onRemove={(id) => watchlist.remove(id)} />
        ) : (
          <p className="text-sm text-white/50">
            Agregá títulos con “Mi Lista” desde cualquier tarjeta.
          </p>
        )}
      </section>
    </div>
  );
}
