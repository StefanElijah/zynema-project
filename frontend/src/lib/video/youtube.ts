const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;

/**
 * Turns a stored trailer URL into a privacy-enhanced embed URL.
 *
 * The catalogue keeps the official YouTube watch link; the player only needs
 * the video id. Anything that is not a YouTube video returns null, which the
 * screens treat as "no trailer".
 */
export function youTubeEmbedUrl(trailerUrl?: string | null): string | null {
  if (!trailerUrl) return null;

  try {
    const url = new URL(trailerUrl);
    const host = url.hostname.replace(/^(www\.|m\.)/, '');
    let id: string | null = null;

    if (host === 'youtu.be') {
      id = url.pathname.split('/').filter(Boolean)[0] ?? null;
    } else if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
      if (/^\/(embed|shorts|live)\//.test(url.pathname)) {
        id = url.pathname.split('/')[2] ?? null;
      } else {
        id = url.searchParams.get('v');
      }
    }

    if (!id || !YOUTUBE_ID.test(id)) return null;
    return `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0`;
  } catch {
    return null;
  }
}
