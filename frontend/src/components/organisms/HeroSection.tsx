import { Link } from 'react-router-dom';
import { Button } from '../ui/button';

interface HeroSectionProps {
  title?: string;
  synopsis?: string;
  tagline?: string;
  backdropUrl?: string;
  detailPath?: string;
  onPlay?: () => void;
}

/**
 * The landing hero. It renders whatever the BFF put in `home.hero` and decides
 * nothing about the catalogue: the title links to its detail page and the
 * primary action starts the same flow the cards do.
 */
export default function HeroSection({
  title,
  synopsis,
  tagline,
  backdropUrl,
  detailPath,
  onPlay,
}: HeroSectionProps) {
  if (!title) {
    return null;
  }

  return (
    <section className="relative overflow-hidden">
      {backdropUrl && (
        <div
          className="absolute inset-0 -z-10 bg-cover bg-center opacity-40"
          style={{ backgroundImage: `url(${backdropUrl})` }}
        />
      )}
      <div className="absolute inset-0 -z-10 bg-gradient-to-t from-black via-black/60 to-transparent" />

      <div className="custom-container py-16 md:py-24">
        {tagline && (
          <p className="mb-2 text-sm uppercase tracking-widest text-white/60">{tagline}</p>
        )}
        <h1 className="text-4xl font-bold md:text-6xl">{title}</h1>
        {synopsis && <p className="mt-4 max-w-xl text-white/80">{synopsis}</p>}

        <div className="mt-6 flex gap-3">
          {onPlay && (
            <Button size="lg" onClick={onPlay}>
              ▶ Reproducir
            </Button>
          )}
          {detailPath && (
            <Button size="lg" variant="secondary" asChild>
              <Link to={detailPath}>Más info</Link>
            </Button>
          )}
        </div>
      </div>
    </section>
  );
}
