import { Link, useSearchParams } from 'react-router-dom';
import { useBrowse, type BrowseType } from '@lib/api';
import ContentSection from '../components/organisms/ContentSection';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';

const SORTS = [
  { value: 'popularity,desc', label: 'Más populares' },
  { value: 'releaseYear,desc', label: 'Más recientes' },
  { value: 'averageRating,desc', label: 'Mejor valoradas' },
] as const;

const PAGE_SIZE = 24;

/**
 * The catalogue, filtered by type and sorted. Every filter lives in the URL,
 * so a browse view is shareable and the back button behaves: the URL is the
 * state and the query key, nothing else.
 */
export default function Browse() {
  const [searchParams, setSearchParams] = useSearchParams();
  const type: BrowseType = searchParams.get('type') === 'MOVIE' ? 'MOVIE' : 'SERIES';
  const sort = searchParams.get('sort') ?? SORTS[0].value;
  const page = Math.max(0, Number(searchParams.get('page') ?? '0'));

  const { data, isLoading, isError, refetch } = useBrowse({
    type,
    sort,
    page,
    size: PAGE_SIZE,
  });

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(searchParams);
    if (value === null) {
      next.delete(key);
    } else {
      next.set(key, value);
    }
    // A new filter means a new first page of results.
    if (key !== 'page') {
      next.delete('page');
    }
    setSearchParams(next, { replace: false });
  };

  const label = type === 'MOVIE' ? 'Películas' : 'Series';

  return (
    <div className="custom-container py-10">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-semibold">{label}</h1>

        <div className="flex items-center gap-1 rounded-md border border-white/15 p-1">
          <Button variant={type === 'SERIES' ? 'default' : 'ghost'} size="sm" asChild>
            <Link to="/catalog?type=SERIES">Series</Link>
          </Button>
          <Button variant={type === 'MOVIE' ? 'default' : 'ghost'} size="sm" asChild>
            <Link to="/catalog?type=MOVIE">Películas</Link>
          </Button>
        </div>
      </div>

      <div className="mt-4 flex items-center gap-2 text-sm text-white/70">
        <label htmlFor="sort">Ordenar por</label>
        <select
          id="sort"
          value={sort}
          onChange={(event) => setParam('sort', event.target.value)}
          className="rounded border border-white/20 bg-neutral-950 px-2 py-1 text-white"
        >
          {SORTS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      {isLoading && (
        <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
          {Array.from({ length: 12 }).map((_, index) => (
            <Skeleton key={index} className="aspect-[2/3] w-full" />
          ))}
        </div>
      )}

      {isError && (
        <div className="py-20 text-center">
          <p className="text-red-400">No pudimos cargar el catálogo.</p>
          <Button variant="outline" className="mt-4" onClick={() => void refetch()}>
            Reintentar
          </Button>
        </div>
      )}

      {data && (data.content?.length ?? 0) > 0 && (
        <div className="mt-8">
          <ContentSection title="" items={data.content} />
        </div>
      )}

      {data && (data.content?.length ?? 0) === 0 && (
        <p className="py-20 text-center text-white/60">No hay títulos con estos filtros.</p>
      )}

      {data && (data.totalPages ?? 0) > 1 && (
        <div className="mt-8 flex items-center justify-center gap-4">
          <Button
            variant="outline"
            disabled={page <= 0}
            onClick={() => setParam('page', String(page - 1))}
          >
            Anterior
          </Button>
          <span className="text-sm text-white/60">
            Página {page + 1} de {data.totalPages}
          </span>
          <Button
            variant="outline"
            disabled={Boolean(data.last)}
            onClick={() => setParam('page', String(page + 1))}
          >
            Siguiente
          </Button>
        </div>
      )}
    </div>
  );
}
