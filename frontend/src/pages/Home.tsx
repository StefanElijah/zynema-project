import { useNavigate } from 'react-router-dom';
import { useHome } from '@lib/api';
import HeroSection from '../components/organisms/HeroSection';
import ContentSection from '../components/organisms/ContentSection';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';

function HomeSkeleton() {
  return (
    <div className="custom-container py-16 md:py-24">
      <Skeleton className="h-10 w-1/2 md:h-14" />
      <Skeleton className="mt-4 h-5 w-2/3 max-w-xl" />
      <div className="mt-6 flex gap-3">
        <Skeleton className="h-10 w-36" />
        <Skeleton className="h-10 w-32" />
      </div>
      <div className="mt-14 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
        {Array.from({ length: 6 }).map((_, index) => (
          <Skeleton key={index} className="aspect-[2/3] w-full" />
        ))}
      </div>
    </div>
  );
}

/**
 * The landing page, composed by the BFF (`GET /web/home`): one hero and the
 * rails the server decided, in the order it decided them. The page renders
 * what it is given — no client-side sorting or hiding — so the two sides
 * cannot disagree about what "popular" means.
 */
export default function Home() {
  const navigate = useNavigate();
  const { data, isLoading, isError, refetch } = useHome();

  if (isLoading) {
    return <HomeSkeleton />;
  }

  if (isError) {
    return (
      <div className="py-24 text-center">
        <p className="text-red-400">No pudimos cargar el catálogo.</p>
        <Button variant="outline" className="mt-4" onClick={() => void refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }

  const heroCard = data?.hero?.card;

  return (
    <>
      <HeroSection
        title={heroCard?.title}
        synopsis={data?.hero?.synopsis}
        tagline={data?.hero?.tagline}
        backdropUrl={heroCard?.backdropUrl ?? heroCard?.posterUrl}
        detailPath={heroCard?.id ? `/title/${heroCard.slug ?? heroCard.id}` : undefined}
        onPlay={heroCard?.id ? () => navigate(`/watch/${heroCard.id}`) : undefined}
      />

      {data?.rows?.map((row) => (
        <ContentSection key={row.id} title={row.title ?? ''} items={row.items ?? []} />
      ))}
    </>
  );
}
