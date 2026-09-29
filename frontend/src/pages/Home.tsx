import HeroSection from '../components/organisms/HeroSection';
import ContentSection from '../components/organisms/ContentSection';

import { content } from '../data/content';
import { getMovies, getRecent, getSeries } from '../data/selectors';

export default function Home() {
  return (
    <>
      <HeroSection />

      <ContentSection title="Recién Agregados" items={getRecent(content)} />

      <ContentSection title="Series Populares" items={getSeries(content)} />

      <ContentSection title="Películas Populares" items={getMovies(content)} />
    </>
  );
}
