import { FormEvent, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useSearch } from '@lib/api';
import ContentSection from '../components/organisms/ContentSection';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Skeleton } from '../components/ui/skeleton';

const PAGE_SIZE = 24;

/**
 * Search results. The committed query lives in the URL (`?q=`) so the page is
 * shareable, and the input keeps its own draft until submit: typing does not
 * fire a request per keystroke, the catalogue is queried on intent.
 */
export default function Search() {
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q')?.trim() ?? '';
  const page = Math.max(0, Number(searchParams.get('page') ?? '0'));

  const [draft, setDraft] = useState(q);

  const { data, isLoading, isError, refetch } = useSearch(
    { q, page, size: PAGE_SIZE },
    { query: { enabled: q.length > 0 } }
  );

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const next = new URLSearchParams();
    if (draft.trim()) {
      next.set('q', draft.trim());
    }
    setSearchParams(next);
  };

  return (
    <div className="custom-container py-10">
      <h1 className="text-3xl font-semibold">Buscar</h1>

      <form onSubmit={submit} className="mt-4 flex max-w-xl gap-2">
        <Input
          type="search"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Títulos, sagas, personas…"
          aria-label="Buscar en el catálogo"
          autoFocus
        />
        <Button type="submit">Buscar</Button>
      </form>

      {q.length === 0 && (
        <p className="py-20 text-center text-white/60">Escribí algo para buscar en el catálogo.</p>
      )}

      {q.length > 0 && isLoading && (
        <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="aspect-[2/3] w-full" />
          ))}
        </div>
      )}

      {q.length > 0 && isError && (
        <div className="py-20 text-center">
          <p className="text-red-400">La búsqueda falló.</p>
          <Button variant="outline" className="mt-4" onClick={() => void refetch()}>
            Reintentar
          </Button>
        </div>
      )}

      {q.length > 0 && data && (data.content?.length ?? 0) === 0 && (
        <p className="py-20 text-center text-white/60">Sin resultados para “{q}”.</p>
      )}

      {q.length > 0 && data && (data.content?.length ?? 0) > 0 && (
        <div className="mt-8">
          <ContentSection title={`Resultados para “${q}”`} items={data.content} />

          {(data.totalPages ?? 0) > 1 && (
            <div className="mt-8 flex items-center justify-center gap-4">
              <Button
                variant="outline"
                disabled={page <= 0}
                onClick={() => {
                  const next = new URLSearchParams(searchParams);
                  next.set('page', String(page - 1));
                  setSearchParams(next);
                }}
              >
                Anterior
              </Button>
              <span className="text-sm text-white/60">
                Página {page + 1} de {data.totalPages}
              </span>
              <Button
                variant="outline"
                disabled={Boolean(data.last)}
                onClick={() => {
                  const next = new URLSearchParams(searchParams);
                  next.set('page', String(page + 1));
                  setSearchParams(next);
                }}
              >
                Siguiente
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
