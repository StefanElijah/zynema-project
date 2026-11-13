import { Link } from 'react-router-dom';
import type { FranchiseCard } from '@lib/api';

interface SagaSectionProps {
  items?: FranchiseCard[];
}

/**
 * The large-card row for sagas/franchises. A saga is not a title: it links to
 * its own screen, where the member movies and series are listed in the curated
 * order the catalogue returns.
 */
export default function SagaSection({ items = [] }: SagaSectionProps) {
  if (items.length === 0) {
    return null;
  }

  return (
    <section className="custom-container mt-12">
      <div className="mb-4 flex items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold md:text-2xl">Sagas y franquicias</h2>
          <p className="mt-1 text-sm text-white/50">
            Historias que continúan. Elegí una para ver el orden completo.
          </p>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((saga) => (
          <Link
            key={saga.id ?? saga.slug}
            to={`/saga/${saga.slug ?? ''}`}
            className="group relative block aspect-[16/9] overflow-hidden rounded-xl ring-1 ring-white/10 transition hover:ring-red-500/60"
          >
            {saga.coverUrl ? (
              <img
                src={saga.coverUrl}
                alt=""
                loading="lazy"
                className="absolute inset-0 h-full w-full object-cover transition duration-500 group-hover:scale-105"
              />
            ) : (
              <div className="absolute inset-0 bg-neutral-900" />
            )}
            <div className="absolute inset-0 bg-gradient-to-t from-black via-black/40 to-transparent" />
            <div className="absolute inset-x-0 bottom-0 p-5">
              <p className="text-xs uppercase tracking-widest text-white/50">
                {saga.itemCount ?? 0} {saga.itemCount === 1 ? 'título' : 'títulos'}
              </p>
              <h3 className="mt-1 text-xl font-semibold text-white">{saga.name}</h3>
              {saga.description && (
                <p className="mt-1 line-clamp-2 text-sm text-white/70">{saga.description}</p>
              )}
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
