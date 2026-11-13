import { Link, useParams } from 'react-router-dom';
import { useFranchise } from '@lib/api';
import ContentSection from '../components/organisms/ContentSection';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';

/** One saga: its cover, description and member titles in curated order. */
export default function Saga() {
  const { slug } = useParams<{ slug: string }>();
  const { data, isLoading, isError, refetch } = useFranchise(slug ?? '', {
    query: { enabled: Boolean(slug) },
  });

  if (isLoading) {
    return (
      <div className="custom-container py-12">
        <Skeleton className="h-64 w-full rounded-xl" />
        <Skeleton className="mt-8 h-8 w-1/3" />
        <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-6">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="aspect-[2/3] w-full" />
          ))}
        </div>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="py-24 text-center">
        <p className="text-red-400">No encontramos esa saga.</p>
        <Button variant="outline" className="mt-4" onClick={() => void refetch()}>
          Reintentar
        </Button>
        <Button variant="ghost" className="mt-4" asChild>
          <Link to="/">Volver al inicio</Link>
        </Button>
      </div>
    );
  }

  const items = data.items ?? [];

  return (
    <div>
      <section className="relative overflow-hidden">
        {data.coverUrl && (
          <div
            className="absolute inset-0 -z-10 bg-cover bg-center opacity-40"
            style={{ backgroundImage: `url(${data.coverUrl})` }}
          />
        )}
        <div className="absolute inset-0 -z-10 bg-gradient-to-t from-black via-black/70 to-transparent" />
        <div className="custom-container py-16">
          <Link to="/" className="text-sm text-white/60 hover:text-white">
            ← Volver al inicio
          </Link>
          <p className="mt-8 text-xs uppercase tracking-[0.2em] text-red-400">Saga</p>
          <h1 className="mt-2 text-4xl font-bold md:text-5xl">{data.name}</h1>
          {data.description && <p className="mt-4 max-w-2xl text-white/75">{data.description}</p>}
          <p className="mt-4 text-sm text-white/50">
            {items.length} {items.length === 1 ? 'título' : 'títulos'} en orden
          </p>
        </div>
      </section>

      <ContentSection
        title=""
        items={items}
        customMargins={false}
        containerClass="custom-container"
      />
    </div>
  );
}
