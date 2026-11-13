-- The Matrix shipped in V2 with NULL artwork even though its local image is
-- present under frontend/public/assets/. Fix the write row and let the read
-- model be rebuilt from it: a raw SQL write bypasses CatalogProjector, and the
-- startup backfill only runs when the projection is empty, so the projection
-- is cleared here on purpose.

UPDATE content
   SET poster_url   = '/assets/movies/images/matrix_1999-3840x2160.jpg',
       backdrop_url = '/assets/movies/images/matrix_1999-3840x2160.jpg',
       updated_at   = now()
 WHERE slug = 'the-matrix';

DELETE FROM content_read_model;
