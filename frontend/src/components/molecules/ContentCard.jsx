import { useState, useRef, useEffect } from 'react';
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
import '../../styles/content-card.css';

const FALLBACK_IMG =
  'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIwIiBoZWlnaHQ9IjE4MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjMTExIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNCIgZmlsbD0iI2ZmZiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPkltYWdlbiBubyBkaXNwb25pYmxlPC90ZXh0Pjwvc3ZnPg==';

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
}) {
  const [showModal, setShowModal] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const [portalPosition, setPortalPosition] = useState({ top: 0, left: 0 });
  const cardRef = useRef(null);
  const hoverTimerRef = useRef(null);
  const leaveTimerRef = useRef(null);

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

  const {
    title,
    genres = [],
    image,
    duration,
    seasons,
    ageRating,
    description,
    rating,
    year,
    cast = [],
    directors = [],
  } = item;

  const handleKeyAction = (fn) => (e) => {
    if (e.type === 'click' || e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      e.stopPropagation();
      fn?.(item);
    }
  };

  const handleInfoClick = (e) => {
    e.stopPropagation();
    e.preventDefault();
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

  const handleCardClick = (e) => {
    if (!e.target.closest('button') && !e.target.closest('a')) {
      handleInfoClick(e);
    }
  };

  const cardBase = (
    <div
      ref={cardRef}
      className={`content-card-wrapper ${variant} ${isHovered ? 'hovered' : ''}`}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onClick={handleCardClick}
      role="button"
      tabIndex={0}
      aria-label={`Ver detalles de ${title}`}
    >
      <div className="content-card">
        <div className="image-wrapper">
          <img
            src={image}
            alt={title}
            className="card-image"
            loading="lazy"
            onError={(e) => {
              e.target.src = FALLBACK_IMG;
            }}
          />
          <div className="card-overlay">
            <div className="overlay-top">
              {ageRating && <span className="age-badge">{ageRating}</span>}
              <div className="overlay-actions">
                <button className="btn-circle btn-play" onClick={handleKeyAction(onPlay)} aria-label="Reproducir">
                  <FaPlay />
                </button>
                <button className="btn-circle" onClick={handleKeyAction(onAdd)} aria-label="Agregar a mi lista">
                  <FaPlus />
                </button>
              </div>
            </div>
            <div className="overlay-bottom">
              <div className="meta-left">
                <div className="meta-line">
                  {year && <span className="meta-year">{year}</span>}
                  {genres.slice(0, 2).map((g) => (
                    <span className="genre-badge" key={g}>{g}</span>
                  ))}
                  {duration ? <span>• {duration} min</span> : seasons ? <span>• {seasons} temp.</span> : null}
                </div>
              </div>
              <div className="meta-right">
                <button className="btn-circle" onClick={handleInfoClick} aria-label="Más información">
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
          <button className="expanded-btn-circle" onClick={handleKeyAction(onAdd)} aria-label="Mi lista">
            <FaPlus />
          </button>
          <button className="expanded-btn-circle" onClick={handleInfoClick} aria-label="Más información">
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
          {year && <div className="meta-item"><FaCalendarAlt /> {year}</div>}
          {duration && <div className="meta-item"><FaClock /> {duration} min</div>}
          {seasons && <div className="meta-item"><FaVideo /> {seasons} temp.</div>}
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
            <button className="btn-link btn-like" onClick={handleKeyAction(onLike)} title="Me gusta"><FaThumbsUp /></button>
            <button className="btn-link btn-dislike" onClick={handleKeyAction(onDislike)} title="No me gusta"><FaThumbsDown /></button>
            <button className="btn-link btn-favorite" onClick={handleKeyAction(onFavorite)} title="Me encanta"><FaHeart /></button>
            <button className="btn-link btn-dismiss" onClick={handleKeyAction(onDismiss)} title="No me interesa"><FaTimes /></button>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <>
      {cardBase}
      {createPortal(expandedContent, document.body)}
      {showModal && createPortal(
        <div className="content-modal-backdrop" onClick={handleCloseModal}>
          <div className="content-modal" onClick={(e) => e.stopPropagation()}>
            <div className="content-modal-header">
              <h2 className="content-modal-title">{title}</h2>
              <button className="content-modal-close" onClick={handleCloseModal} aria-label="Cerrar">×</button>
            </div>
            <div className="content-modal-body">
              <div className="modal-image-container">
                <img src={image} alt={title} className="modal-image" />
                <button
                  className="modal-play-btn"
                  onClick={() => { onPlay?.(item); handleCloseModal(); }}
                >
                  <FaPlay className="me-2" /> Reproducir
                </button>
              </div>
              <div className="modal-details">
                <div className="modal-meta">
                  {ageRating && <span className="age-badge">{ageRating}</span>}
                  {year && <span>{year}</span>}
                  {duration ? <span>{duration} min</span> : seasons ? <span>{seasons} temp.</span> : null}
                  {rating && <span className="rating-badge"><FaStar className="me-1" /> {rating}/10</span>}
                </div>
                <div className="modal-genres">
                  {genres.map((g, i) => <span className="genre-badge" key={i}>{g}</span>)}
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
                    <button className="btn-icon" onClick={() => onLike?.(item)} aria-label="Me gusta"><FaThumbsUp /></button>
                    <button className="btn-icon" onClick={() => onDislike?.(item)} aria-label="No me gusta"><FaThumbsDown /></button>
                    <button className="btn-icon" onClick={() => onFavorite?.(item)} aria-label="Me encanta"><FaHeart /></button>
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
