import { Link, useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import ContentCard from '../molecules/ContentCard';
import type { TitleCard } from '@lib/api';
import { useWatchlistActions } from '@hooks/useWatchlistActions';
import '../../styles/content-section.css';

interface ContentSectionProps {
  title: string;
  items?: TitleCard[];
  containerClass?: string;
  customMargins?: boolean;
  /** When present, the header links to the full, filtered catalogue view. */
  moreHref?: string;
  moreLabel?: string;
}

/**
 * A row of the catalogue, from the BFF's `TitleCard` shape.
 *
 * The two actions that exist in the domain are the two the card shows:
 * play (the player) and add to the list (the watchlist, which needs a
 * profile — the card silently does nothing until one is selected).
 */
export default function ContentSection({
  title,
  items = [],
  containerClass = 'custom-container',
  customMargins = true,
  moreHref,
  moreLabel = 'Ver todo',
}: ContentSectionProps) {
  const navigate = useNavigate();
  const watchlist = useWatchlistActions();

  if (items.length === 0) {
    return null;
  }

  return (
    <section className={`content-section ${customMargins ? 'with-custom-margins' : ''}`}>
      <div className={containerClass}>
        {(title || moreHref) && (
          <div className="mb-4 flex items-center justify-between gap-4">
            <h2 className="section-title">{title}</h2>
            {moreHref && (
              <Link
                to={moreHref}
                aria-label={title ? `${moreLabel} ${title}` : moreLabel}
                className="flex shrink-0 items-center gap-1 text-sm text-white/60 transition hover:text-white"
              >
                {moreLabel}
                <ChevronRight className="size-4" aria-hidden="true" />
              </Link>
            )}
          </div>
        )}
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-3">
          {items.map((item) => (
            <div key={item.id} className="mb-3">
              <ContentCard
                item={item}
                onPlay={() => navigate(`/watch/${item.id}`)}
                onAdd={() => watchlist.add(item.id)}
              />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
