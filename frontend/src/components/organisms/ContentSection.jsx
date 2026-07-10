import ContentCard from '../molecules/ContentCard';
import '../../styles/content-section.css';

export default function ContentSection({
  title,
  items = [],
  containerClass = 'custom-container',
  customMargins = true,
}) {
  const handlePlay = (item) => { console.log('play', item.id); };
  const handleAdd = (item) => { console.log('add', item.id); };
  const handleLike = (item) => { console.log('like', item.id); };
  const handleDislike = (item) => { console.log('dislike', item.id); };
  const handleFavorite = (item) => { console.log('fav', item.id); };
  const handleDismiss = (item) => { console.log('dismiss', item.id); };

  return (
    <section className={`content-section ${customMargins ? 'with-custom-margins' : ''}`}>
      <div className={containerClass}>
        <h2 className="section-title mb-4">{title}</h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 gap-3">
          {items.map((item) => (
            <div key={item.id} className="mb-3">
              <ContentCard
                item={item}
                onPlay={handlePlay}
                onAdd={handleAdd}
                onLike={handleLike}
                onDislike={handleDislike}
                onFavorite={handleFavorite}
                onDismiss={handleDismiss}
              />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
