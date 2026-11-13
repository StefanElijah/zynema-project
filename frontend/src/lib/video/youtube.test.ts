import { describe, expect, it } from 'vitest';
import { youTubeEmbedUrl } from './youtube';

const EMBED = 'https://www.youtube-nocookie.com/embed/Way9Dexny3w?autoplay=1&rel=0';

describe('youTubeEmbedUrl', () => {
  it('accepts the canonical watch URL', () => {
    expect(youTubeEmbedUrl('https://www.youtube.com/watch?v=Way9Dexny3w')).toBe(EMBED);
  });

  it('accepts short links and extra query parameters', () => {
    expect(youTubeEmbedUrl('https://youtu.be/Way9Dexny3w?t=42')).toBe(EMBED);
    expect(youTubeEmbedUrl('https://www.youtube.com/watch?v=Way9Dexny3w&list=abc')).toBe(EMBED);
  });

  it('accepts embed, shorts and live URLs', () => {
    expect(youTubeEmbedUrl('https://www.youtube.com/embed/Way9Dexny3w')).toBe(EMBED);
    expect(youTubeEmbedUrl('https://www.youtube.com/shorts/Way9Dexny3w')).toBe(EMBED);
    expect(youTubeEmbedUrl('https://www.youtube.com/live/Way9Dexny3w')).toBe(EMBED);
  });

  it('rejects non-YouTube and malformed URLs', () => {
    expect(youTubeEmbedUrl('https://vimeo.com/12345')).toBeNull();
    expect(youTubeEmbedUrl('not a url')).toBeNull();
    expect(youTubeEmbedUrl('https://www.youtube.com/watch?v=too-short')).toBeNull();
    expect(youTubeEmbedUrl(null)).toBeNull();
    expect(youTubeEmbedUrl(undefined)).toBeNull();
    expect(youTubeEmbedUrl('')).toBeNull();
  });
});
