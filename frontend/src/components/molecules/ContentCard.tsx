import {
  MouseEvent as ReactMouseEvent,
  KeyboardEvent as ReactKeyboardEvent,
  SyntheticEvent,
  useEffect,
  useRef,
  useState,
} from 'react';
import { createPortal } from 'react-dom';
import {
  FaPlay,
  FaPlus,
  FaThumbsUp,
  FaThumbsDown,
  FaHeart,
  FaTimes,
  FaInfoCircle,
  FaStar,
  FaCalendarAlt,
  FaClock,
  FaVideo,
} from 'react-icons/fa';
import type { TitleCard } from '@lib/api';
import '../../styles/content-card.css';

const FALLBACK_IMG =
  'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIwIiBoZWlnaHQ9IjE4MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjMTExIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNCIgZmlsbD0iI2ZmZiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPkltYWdlbiBubyBkaXNwb25pYmxlPC90ZXh0Pjwvc3ZnPg==';

interface ContentCardProps {
  item: TitleCard | null;
  onPlay?: (item: TitleCard) => void;
  onAdd?: (item: TitleCard) => void;
  onLike?: (item: TitleCard) => void;
  onDislike?: (item: TitleCard) => void;
  onFavorite?: (item: TitleCard) => void;
  onDismiss?: (item: TitleCard) => void;
  variant?: string;
  hoverDelay?: number;
}

export default function ContentCard({
  item,
  onPlay,
  onAdd,
  onLike,
  onDislike,
  onFavorite,
  onDismiss,
  variant = 'default',
  hoverDelay = 300,
}: ContentCardProps) {
  const [showModal, setShowModal] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const [portalPosition, setPortalPosition] = useState({ top: 0, left: 0, origin: 'top left' });
  const cardRef = useRef<HTMLDivElement>(null);
  const hoverTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const leaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (hoverTimerRef.current) clearTimeout(hoverTimerRef.current);
      if (leaveTimerRef.current) clearTimeout(leaveTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (isHovered && cardRef.current) {
      const rect = cardRef.current.getBoundingClientRect();
      const viewportWidth = window.innerWidth;
      const expandedWidth = 320;
      let left = rect.left;
      let origin = 'top left';

      const spaceOnRight = viewportWidth - rect.right;
      const spaceOnLeft = rect.left;

      if (spaceOnLeft >= expandedWidth / 2 && spaceOnRight >= expandedWidth / 2) {
        left = rect.left + rect.width / 2 - expandedWidth / 2;
        origin = 'top center';
      } else if (spaceOnRight < expandedWidth && spaceOnLeft >= expandedWidth) {
        left = rect.left;
        origin = 'top left';
      } else if (spaceOnLeft < expandedWidth && spaceOnRight >= expandedWidth) {
        left = rect.right - expandedWidth;
        origin = 'top right';
      } else {
        left = Math.max(8, Math.min(viewportWidth - expandedWidth - 8, rect.left));
        origin = 'top left';
      }

      setPortalPosition({ top: rect.top + window.scrollY, left, origin });
    }
  }, [isHovered]);

  if (!item) return null;

  // The BFF's TitleCard is the card's data source. The layout survives fields
  // the card used to get from the mock catalogue (seasons, synopsis, cast):
  // without them those lines simply do not render.
  const { title } = item;
  const genres = (item.genres ?? []).map((genre) => genre.name ?? '').filter(Boolean);
  const image = item.posterUrl ?? item.backdropUrl ?? FALLBACK_IMG;
  const duration = undefined as number | undefined;
  const seasons = undefined as number | undefined;
  const ageRating = item.maturityRating;
  const description = undefined as string | undefined;
  const rating = item.averageRating;
  const year = item.releaseYear;
  const cast: string[] = [];
  const directors: string[] = [];

  const handleKeyAction =
    (fn?: (item: TitleCard) => void) => (event: ReactMouseEvent | ReactKeyboardEvent) => {
      const isActivationKey = 'key' in event && (event.key === 'Enter' || event.key === ' ');
      if (event.type === 'click' || isActivationKey) {
        event.preventDefault();
        event.stopPropagation();
        fn?.(item);
      }
    };

  const handleInfoClick = (event: ReactMouseEvent | ReactKeyboardEvent) => {
    event.stopPropagation();
    event.preventDefault();
    setShowModal(true);
    setIsHovered(false);
    if (hoverTimerRef.current) clearTimeout(hoverTimerRef.current);
    if (leaveTimerRef.current) clearTimeout(leaveTimerRef.current);
  };

  const handleCloseModal = () => setShowModal(false);

  const handleMouseEnter = () => {
    if (leaveTimerRef.current) clearTimeout(leaveTimerRef.current);
    if (hoverTimerRef.current) return;
    hoverTimerRef.current = setTimeout(() => {
      setIsHovered(true);
      hoverTimerRef.current = null;
    }, hoverDelay);
  };

  const handleMouseLeave = () => {
    if (hoverTimerRef.current) {
      clearTimeout(hoverTimerRef.current);
      hoverTimerRef.current = null;
    }
    if (leaveTimerRef.current) clearTimeout(leaveTimerRef.current);
    leaveTimerRef.current = setTimeout(() => setIsHovered(false), 150);
  };

  const handleCardClick = (event: ReactMouseEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement;
    if (!target.closest('button') && !target.closest('a')) {
      handleInfoClick(event);
    }
  };

  const cardBase = (
    // A plain container with a click handler for mouse users: the card cannot
    // be a <button> because it contains buttons of its own, and faking the role
    // on a div is exactly what Sonar (S6819) flags. Keyboard users get the real
    // buttons: "Reproducir" and "Más información" both work without a mouse.
    <div
      ref={cardRef}
      className={`content-card-wrapper ${variant} ${isHovered ? 'hovered' : ''}`}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onClick={handleCardClick}
    >
      <div className="content-card">
        <div className="image-wrapper">
          <img
            src={image}
            alt={title}
            className="card-image"
            loading="lazy"
            onError={(event: SyntheticEvent<HTMLImageElement>) => {
              event.currentTarget.src = FALLBACK_IMG;
            }}
          />
          <div className="card-overlay">
            <div className="overlay-top">
              {ageRating && <span className="age-badge">{ageRating}</span>}
              <div className="overlay-actions">
                <button
                  className="btn-circle btn-play"
                  onClick={handleKeyAction(onPlay)}
                  aria-label="Reproducir"
                >
                  <FaPlay />
                </button>
                <button
                  className="btn-circle"
                  onClick={handleKeyAction(onAdd)}
                  aria-label="Agregar a mi lista"
                >
                  <FaPlus />
                </button>
              </div>
            </div>
            <div className="overlay-bottom">
              <div className="meta-left">
                <div className="meta-line">
                  {year && <span className="meta-year">{year}</span>}
                  {genres.slice(0, 2).map((genre) => (
                    <span className="genre-badge" key={genre}>
                      {genre}
                    </span>
                  ))}
                  {duration ? (
                    <span>· {duration} min</span>
                  ) : seasons ? (
                    <span>· {seasons} temp.</span>
                  ) : null}
                </div>
              </div>
              <div className="meta-right">
                <button
                  className="btn-circle"
                  onClick={handleInfoClick}
                  aria-label="Más información"
                >
                  <FaInfoCircle />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  const expandedContent = isHovered && (
    <div
      className="portal-card-expanded"
      style={{
        top: portalPosition.top,
        left: portalPosition.left,
        transformOrigin: portalPosition.origin,
      }}
      onMouseEnter={() => {
        if (leaveTimerRef.current) clearTimeout(leaveTimerRef.current);
      }}
      onMouseLeave={handleMouseLeave}
    >
      <div className="image-wrapper">
        <img src={image} alt={title} className="card-image" />
        <div className="expanded-overlay-top">
          <button
            className="expanded-btn-circle"
            onClick={handleKeyAction(onAdd)}
            aria-label="Mi lista"
          >
            <FaPlus />
          </button>
          <button
            className="expanded-btn-circle"
            onClick={handleInfoClick}
            aria-label="Más información"
          >
            <FaInfoCircle />
          </button>
        </div>
      </div>
      <div className="expanded-content">
        <h5 className="expanded-title">{title}</h5>
        <div className="expanded-meta">
          {rating && (
            <div className="meta-item">
              <FaStar className="rating-star" /> {rating}/10
            </div>
          )}
          {year && (
            <div className="meta-item">
              <FaCalendarAlt /> {year}
            </div>
          )}
          {duration && (
            <div className="meta-item">
              <FaClock /> {duration} min
            </div>
          )}
          {seasons && (
            <div className="meta-item">
              <FaVideo /> {seasons} temp.
            </div>
          )}
        </div>
        {description && (
          <p className="expanded-description">
            {description.length > 120 ? description.substring(0, 120) + '...' : description}
          </p>
        )}
        <div className="expanded-actions">
          <button className="btn-play-expanded" onClick={handleKeyAction(onPlay)}>
            <FaPlay className="me-1" /> Reproducir
          </button>
          <button className="btn-add-expanded" onClick={handleKeyAction(onAdd)}>
            <FaPlus className="me-1" /> Mi Lista
          </button>
          <div className="expanded-quick-actions">
            <button
              className="btn-link btn-like"
              onClick={handleKeyAction(onLike)}
              title="Me gusta"
            >
              <FaThumbsUp />
            </button>
            <button
              className="btn-link btn-dislike"
              onClick={handleKeyAction(onDislike)}
              title="No me gusta"
            >
              <FaThumbsDown />
            </button>
            <button
              className="btn-link btn-favorite"
              onClick={handleKeyAction(onFavorite)}
              title="Me encanta"
            >
              <FaHeart />
            </button>
            <button
              className="btn-link btn-dismiss"
              onClick={handleKeyAction(onDismiss)}
              title="No me interesa"
            >
              <FaTimes />
            </button>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <>
      {cardBase}
      {createPortal(expandedContent, document.body)}
      {showModal &&
        createPortal(
          <div className="content-modal-backdrop" onClick={handleCloseModal}>
            <div className="content-modal" onClick={(event) => event.stopPropagation()}>
              <div className="content-modal-header">
                <h2 className="content-modal-title">{title}</h2>
                <button
                  className="content-modal-close"
                  onClick={handleCloseModal}
                  aria-label="Cerrar"
                >
                  ×
                </button>
              </div>
              <div className="content-modal-body">
                <div className="modal-image-container">
                  <img src={image} alt={title} className="modal-image" />
                  <button
                    className="modal-play-btn"
                    onClick={() => {
                      onPlay?.(item);
                      handleCloseModal();
                    }}
                  >
                    <FaPlay className="me-2" /> Reproducir
                  </button>
                </div>
                <div className="modal-details">
                  <div className="modal-meta">
                    {ageRating && <span className="age-badge">{ageRating}</span>}
                    {year && <span>{year}</span>}
                    {duration ? (
                      <span>{duration} min</span>
                    ) : seasons ? (
                      <span>{seasons} temp.</span>
                    ) : null}
                    {rating && (
                      <span className="rating-badge">
                        <FaStar className="me-1" /> {rating}/10
                      </span>
                    )}
                  </div>
                  <div className="modal-genres">
                    {genres.map((genre, index) => (
                      <span className="genre-badge" key={index}>
                        {genre}
                      </span>
                    ))}
                  </div>
                  {description && (
                    <div className="modal-description">
                      <h6>Sinopsis</h6>
                      <p>{description}</p>
                    </div>
                  )}
                  {cast.length > 0 && (
                    <div className="modal-cast">
                      <h6>Reparto</h6>
                      <p>{cast.join(', ')}</p>
                    </div>
                  )}
                  {directors.length > 0 && (
                    <div className="modal-directors">
                      <h6>Director{directors.length > 1 ? 'es' : ''}</h6>
                      <p>{directors.join(', ')}</p>
                    </div>
                  )}
                  <div className="modal-actions">
                    <button className="btn-outline-light" onClick={() => onAdd?.(item)}>
                      <FaPlus className="me-1" /> Mi Lista
                    </button>
                    <div className="inline-flex gap-2">
                      <button
                        className="btn-icon"
                        onClick={() => onLike?.(item)}
                        aria-label="Me gusta"
                      >
                        <FaThumbsUp />
                      </button>
                      <button
                        className="btn-icon"
                        onClick={() => onDislike?.(item)}
                        aria-label="No me gusta"
                      >
                        <FaThumbsDown />
                      </button>
                      <button
                        className="btn-icon"
                        onClick={() => onFavorite?.(item)}
                        aria-label="Me encanta"
                      >
                        <FaHeart />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>,
          document.body
        )}
    </>
  );
}
