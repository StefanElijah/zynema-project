-- ══════════════════════════════════════════════════════════════════════
-- V8__add_movie_artwork_and_titles.sql
--
-- Adds the artwork-driven movie batch (Batman/Matrix/Dune/DC/Pacific Rim),
-- the missing Star Wars episodes and the MonsterVerse, with full metadata,
-- genres, credits, trailers and local artwork. Also replaces The Matrix
-- artwork with the AVIF master and completes the cast of the titles that
-- received artwork in this batch.
--
-- The detail view is served from content_read_model and its cache. Raw SQL
-- writes bypass the projector, so the projection is cleared at the end and
-- the startup backfill rebuilds it from the write tables.
-- ══════════════════════════════════════════════════════════════════════

-- ─────────────────────── existing titles: artwork ─────────────────────

UPDATE content
SET poster_url = '/assets/movies/images/the_dark_knight-2008-3840x2160.avif',
    backdrop_url = '/assets/movies/images/the_dark_knight-2008-3840x2160.avif',
    metadata = jsonb_set(metadata, '{franchise}', '"The Dark Knight Trilogy"'),
    updated_at = now()
WHERE slug = 'the-dark-knight';

UPDATE content
SET poster_url = '/assets/movies/images/oppenheimer-2023-3840x2160.avif',
    backdrop_url = '/assets/movies/images/oppenheimer-2023-3840x2160.avif',
    updated_at = now()
WHERE slug = 'oppenheimer';

UPDATE content
SET poster_url = '/assets/movies/images/mad_max_fury_road-2015-3840x2160.avif',
    backdrop_url = '/assets/movies/images/mad_max_fury_road-2015-3840x2160.avif',
    metadata = jsonb_set(metadata, '{franchise}', '"Mad Max"'),
    updated_at = now()
WHERE slug = 'mad-max-fury-road';

UPDATE content
SET poster_url = '/assets/movies/images/matrix_1999-3840x2160.avif',
    backdrop_url = '/assets/movies/images/matrix_1999-3840x2160.avif',
    metadata = jsonb_set(metadata, '{franchise}', '"The Matrix"'),
    updated_at = now()
WHERE slug = 'the-matrix';

-- Existing series that are members of sagas and had NULL artwork.
UPDATE content
SET poster_url = '/assets/series/images/andor-serie-3840x2160.jpg',
    backdrop_url = '/assets/series/images/andor-serie-3840x2160.jpg',
    updated_at = now()
WHERE slug = 'andor';

UPDATE content
SET poster_url = '/assets/series/images/the-mandalorian-serie-3840x2160.jpg',
    backdrop_url = '/assets/series/images/the-mandalorian-serie-3840x2160.jpg',
    updated_at = now()
WHERE slug = 'the-mandalorian';

UPDATE content
SET poster_url = '/assets/series/images/breaking-bad-serie-3840x2160.jpg',
    backdrop_url = '/assets/series/images/breaking-bad-serie-3840x2160.jpg',
    updated_at = now()
WHERE slug = 'breaking-bad';

UPDATE content
SET poster_url = '/assets/series/images/better-call-saul-serie-3840x2160.jpg',
    backdrop_url = '/assets/series/images/better-call-saul-serie-3840x2160.jpg',
    updated_at = now()
WHERE slug = 'better-call-saul';

UPDATE content
SET poster_url = '/assets/series/images/house-of-the-dragon-serie-3840x2160.jpg',
    backdrop_url = '/assets/series/images/house-of-the-dragon-serie-3840x2160.jpg',
    updated_at = now()
WHERE slug = 'house-of-the-dragon';

-- ──────────────────────────── new movies ─────────────────────────────

INSERT INTO content (id, type, title, original_title, slug, synopsis, tagline, release_year,
                     maturity_rating, runtime_minutes, poster_url, backdrop_url, trailer_url,
                     average_rating, popularity, metadata)
VALUES
    ('a1000000-0000-4000-8000-000000000029', 'MOVIE', 'Batman Begins', NULL, 'batman-begins',
     'A young Bruce Wayne travels the world seeking the means to fight injustice and returns to Gotham to become Batman, only to face a conspiracy that threatens the city.',
     'Evil fears the knight.', 2005, 'PG-13', 140,
     '/assets/movies/images/batman_begins-2005-3840x2160.avif',
     '/assets/movies/images/batman_begins-2005-3840x2160.avif',
     'https://www.youtube.com/watch?v=QhPqez3CwiM', 8.2, 86,
     '{"country": "US", "language": "en", "franchise": "The Dark Knight Trilogy", "box_office_usd": 375000000}'),

    ('a1000000-0000-4000-8000-000000000030', 'MOVIE', 'The Dark Knight Rises', NULL, 'the-dark-knight-rises',
     'Eight years after the events of The Dark Knight, Bruce Wayne returns as Batman when a masked mercenary named Bane threatens to destroy Gotham.',
     'The legend ends.', 2012, 'PG-13', 164,
     '/assets/movies/images/the_dark_knight_rises-2012-3840x2160.avif',
     '/assets/movies/images/the_dark_knight_rises-2012-3840x2160.avif',
     'https://www.youtube.com/watch?v=g8evyE9TuYk', 8.4, 91,
     '{"country": "US", "language": "en", "franchise": "The Dark Knight Trilogy", "box_office_usd": 1081000000}'),

    ('a1000000-0000-4000-8000-000000000031', 'MOVIE', 'Batman v Superman: Dawn of Justice', NULL, 'batman-v-superman-dawn-of-justice',
     'Fearing the actions of a god-like Superman, Batman takes on the Man of Steel while the world wrestles with what kind of hero it needs.',
     'Who will win?', 2016, 'PG-13', 151,
     '/assets/movies/images/batman_v_superman_dawn_of_justice-2016-3840x2160.avif',
     '/assets/movies/images/batman_v_superman_dawn_of_justice-2016-3840x2160.avif',
     'https://www.youtube.com/watch?v=0WWzgGyAH6Y', 6.5, 83,
     '{"country": "US", "language": "en", "franchise": "DC Extended Universe", "box_office_usd": 874400000}'),

    ('a1000000-0000-4000-8000-000000000032', 'MOVIE', 'Man of Steel', NULL, 'man-of-steel',
     'An alien child raised on Earth discovers his extraordinary powers and must decide what kind of hero he will become when his homeworld''s surviving forces threaten the planet.',
     'You will believe that a man can fly.', 2013, 'PG-13', 143,
     '/assets/movies/images/man_of_steel-2013-3840x2160.avif',
     '/assets/movies/images/man_of_steel-2013-3840x2160.avif',
     'https://www.youtube.com/watch?v=KVu3gS7iJu4', 7.1, 85,
     '{"country": "US", "language": "en", "franchise": "DC Extended Universe", "box_office_usd": 668000000}'),

    ('a1000000-0000-4000-8000-000000000033', 'MOVIE', 'Pacific Rim', NULL, 'pacific-rim',
     'Humanity builds giant robots to fight monstrous creatures rising from the sea, and a washed-up pilot is called back to lead one last stand.',
     'To fight monsters, we created monsters.', 2013, 'PG-13', 131,
     '/assets/movies/images/pacific_rim-2013-3840x2160.avif',
     '/assets/movies/images/pacific_rim-2013-3840x2160.avif',
     'https://www.youtube.com/watch?v=5guMumPFBag', 6.9, 81,
     '{"country": "US", "language": "en", "franchise": "Pacific Rim", "box_office_usd": 411000000}'),

    ('a1000000-0000-4000-8000-000000000034', 'MOVIE', 'The Matrix Reloaded', NULL, 'the-matrix-reloaded',
     'Neo and the resistance continue their fight against the machines as the path to Zion and the truth about the Matrix comes into focus.',
     'Free your mind.', 2003, 'R', 138,
     '/assets/movies/images/the_matrix_reloaded-2003-3840x2160.avif',
     '/assets/movies/images/the_matrix_reloaded-2003-3840x2160.avif',
     'https://www.youtube.com/watch?v=zmYE3tg26Qc', 7.2, 84,
     '{"country": "US", "language": "en", "franchise": "The Matrix", "box_office_usd": 741000000}'),

    ('a1000000-0000-4000-8000-000000000035', 'MOVIE', 'The Matrix Revolutions', NULL, 'the-matrix-revolutions',
     'The war between humans and machines reaches its final battle as Neo journeys toward the source of the Matrix and a decisive confrontation.',
     'Everything that has a beginning has an end.', 2003, 'R', 129,
     '/assets/movies/images/the_matrix_revolutions-2003-3840x2160.avif',
     '/assets/movies/images/the_matrix_revolutions-2003-3840x2160.avif',
     'https://www.youtube.com/watch?v=dyZ_kGhiB4U', 6.7, 80,
     '{"country": "US", "language": "en", "franchise": "The Matrix", "box_office_usd": 427000000}'),

    ('a1000000-0000-4000-8000-000000000036', 'MOVIE', 'Dune: Part Three', NULL, 'dune-part-three',
     'Emperor Paul Atreides faces the political and religious forces he unleashed as his rule over the known universe threatens to spiral beyond his control.',
     'The final chapter.', 2026, 'PG-13', NULL,
     '/assets/movies/images/dune_part_three-2026-3840x2160.avif',
     '/assets/movies/images/dune_part_three-2026-3840x2160.avif',
     'https://www.youtube.com/watch?v=NdvqHc56lE0', NULL, 98,
     '{"country": "US", "language": "en", "franchise": "Dune"}'),

    ('a1000000-0000-4000-8000-000000000037', 'MOVIE', 'Star Wars: Episode I - The Phantom Menace', NULL, 'star-wars-episode-i-the-phantom-menace',
     'A young Anakin Skywalker is discovered on Tatooine while the Jedi uncover a plot that will reshape the galaxy.',
     'Every saga has a beginning.', 1999, 'PG', 136,
     '/assets/movies/images/star-wars-episode-i-the-phantom-menace-3840x2160.jpg',
     '/assets/movies/images/star-wars-episode-i-the-phantom-menace-3840x2160.jpg',
     'https://www.youtube.com/watch?v=bD7bpG-zDJQ', 6.5, 88,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "box_office_usd": 1027000000}'),

    ('a1000000-0000-4000-8000-000000000038', 'MOVIE', 'Star Wars: Episode II - Attack of the Clones', NULL, 'star-wars-episode-ii-attack-of-the-clones',
     'Ten years after The Phantom Menace, Anakin Skywalker edges closer to the dark side as the Republic slides toward war.',
     'A galaxy divided.', 2002, 'PG', 142,
     '/assets/movies/images/star-wars-episode-ii-attack-of-the-clones-3840x2160.jpg',
     '/assets/movies/images/star-wars-episode-ii-attack-of-the-clones-3840x2160.jpg',
     'https://www.youtube.com/watch?v=gYbW1F_c9eM', 6.6, 86,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "box_office_usd": 653000000}'),

    ('a1000000-0000-4000-8000-000000000039', 'MOVIE', 'Star Wars: Episode IV - A New Hope', NULL, 'star-wars-episode-iv-a-new-hope',
     'A farm boy joins a rebel alliance to rescue a princess and destroy the Empire''s ultimate weapon.',
     'May the Force be with you.', 1977, 'PG', 121,
     '/assets/movies/images/star-wars-episode-iv-a-new-hope-3840x2160.jpg',
     '/assets/movies/images/star-wars-episode-iv-a-new-hope-3840x2160.jpg',
     'https://www.youtube.com/watch?v=vZ734NWnAHA', 8.6, 94,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "box_office_usd": 775000000}'),

    ('a1000000-0000-4000-8000-000000000040', 'MOVIE', 'Star Wars: Episode V - The Empire Strikes Back', NULL, 'star-wars-episode-v-the-empire-strikes-back',
     'The Rebels are scattered after a crushing defeat, and Luke Skywalker seeks a Jedi master while Darth Vader hunts him across the galaxy.',
     'The adventure continues.', 1980, 'PG', 124,
     '/assets/movies/images/star-wars-episode-v-the-empire-strikes-back-3840x2160.jpg',
     '/assets/movies/images/star-wars-episode-v-the-empire-strikes-back-3840x2160.jpg',
     'https://www.youtube.com/watch?v=JNwNXF9Y6kY', 8.7, 95,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "box_office_usd": 538000000}'),

    ('a1000000-0000-4000-8000-000000000041', 'MOVIE', 'Star Wars: Episode VI - Return of the Jedi', NULL, 'star-wars-episode-vi-return-of-the-jedi',
     'Luke Skywalker and the Rebel Alliance launch a final assault on the Empire while Luke confronts Darth Vader and Emperor Palpatine.',
     'The saga continues.', 1983, 'PG', 131,
     '/assets/movies/images/star-wars-episode-vi-return-of-the-jedi-2880x1620.jpg',
     '/assets/movies/images/star-wars-episode-vi-return-of-the-jedi-2880x1620.jpg',
     'https://www.youtube.com/watch?v=7L8p7_SLzvU', 8.3, 92,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "box_office_usd": 475000000}'),

    ('a1000000-0000-4000-8000-000000000042', 'MOVIE', 'Godzilla', NULL, 'godzilla-2014',
     'A soldier and a scientist race to stop a pair of ancient monsters from destroying humanity while the military tries to contain the threat.',
     'The king is here.', 2014, 'PG-13', 123,
     '/assets/movies/images/godzilla-2014-3840x2160.jpg',
     '/assets/movies/images/godzilla-2014-3840x2160.jpg',
     'https://www.youtube.com/watch?v=vIu85WQTPRc', 6.4, 84,
     '{"country": "US", "language": "en", "franchise": "MonsterVerse", "box_office_usd": 529000000}'),

    ('a1000000-0000-4000-8000-000000000043', 'MOVIE', 'Kong: Skull Island', NULL, 'kong-skull-island',
     'A team of explorers journeys to an uncharted island where they encounter a giant ape and discover the truth about the island''s monsters.',
     'The legend comes home.', 2017, 'PG-13', 118,
     '/assets/movies/images/kong-skull-island-3840x2160.jpg',
     '/assets/movies/images/kong-skull-island-3840x2160.jpg',
     'https://www.youtube.com/watch?v=44LdLqgOpjo', 6.7, 85,
     '{"country": "US", "language": "en", "franchise": "MonsterVerse", "box_office_usd": 566000000}'),

    ('a1000000-0000-4000-8000-000000000044', 'MOVIE', 'Godzilla: King of the Monsters', NULL, 'godzilla-king-of-the-monsters',
     'Humanity''s future hangs in the balance as Godzilla and other ancient titans clash after a wave of monster awakenings.',
     'Rule the world.', 2019, 'PG-13', 132,
     '/assets/movies/images/godzilla-king-of-the-monsters-3840x2160.jpg',
     '/assets/movies/images/godzilla-king-of-the-monsters-3840x2160.jpg',
     'https://www.youtube.com/watch?v=QFxN2oDKk0E', 6.0, 80,
     '{"country": "US", "language": "en", "franchise": "MonsterVerse", "box_office_usd": 386000000}'),

    ('a1000000-0000-4000-8000-000000000045', 'MOVIE', 'Godzilla vs. Kong', NULL, 'godzilla-vs-kong',
     'Godzilla and Kong clash in a battle for supremacy while a conspiracy threatens both monsters and the world they inhabit.',
     'One will fall.', 2021, 'PG-13', 113,
     '/assets/movies/images/godzilla-vs-kong-3840x2160.jpg',
     '/assets/movies/images/godzilla-vs-kong-3840x2160.jpg',
     'https://www.youtube.com/watch?v=odM92ap8_c0', 6.3, 86,
     '{"country": "US", "language": "en", "franchise": "MonsterVerse", "box_office_usd": 470000000}'),

    ('a1000000-0000-4000-8000-000000000046', 'MOVIE', 'Godzilla x Kong: The New Empire', NULL, 'godzilla-x-kong-the-new-empire',
     'Godzilla and Kong must team up against a hidden threat from the Hollow Earth that could destroy their world.',
     'Rise together.', 2024, 'PG-13', 115,
     '/assets/movies/images/godzilla-x-kong-the-new-empire-3840x2160.jpg',
     '/assets/movies/images/godzilla-x-kong-the-new-empire-3840x2160.jpg',
     'https://www.youtube.com/watch?v=lV1OOlGwExM', 6.5, 88,
     '{"country": "US", "language": "en", "franchise": "MonsterVerse", "box_office_usd": 572000000}'),

    ('a1000000-0000-4000-8000-000000000047', 'MOVIE', 'Godzilla x Kong: Supernova', NULL, 'godzilla-x-kong-supernova',
     'Godzilla and Kong face off against a cataclysmic threat.',
     'The next chapter of the MonsterVerse.', 2027, NULL, 120,
     '/assets/movies/images/godzilla-x-kong-supernova-1325x1988.jpg',
     '/assets/movies/images/godzilla-x-kong-supernova-1280x720.jpg',
     'https://www.youtube.com/watch?v=pa3nW0EYYcQ', NULL, 90,
     '{"country": "US", "language": "en", "franchise": "MonsterVerse"}');

-- ─────────────────────────────── genres ───────────────────────────────

INSERT INTO content_genres (content_id, genre_id)
SELECT c.id, g.id
FROM (VALUES
    ('batman-begins', 'action'), ('batman-begins', 'adventure'), ('batman-begins', 'crime'), ('batman-begins', 'drama'),
    ('the-dark-knight-rises', 'action'), ('the-dark-knight-rises', 'crime'), ('the-dark-knight-rises', 'drama'), ('the-dark-knight-rises', 'thriller'),
    ('batman-v-superman-dawn-of-justice', 'action'), ('batman-v-superman-dawn-of-justice', 'adventure'), ('batman-v-superman-dawn-of-justice', 'sci-fi'), ('batman-v-superman-dawn-of-justice', 'drama'),
    ('man-of-steel', 'action'), ('man-of-steel', 'adventure'), ('man-of-steel', 'sci-fi'), ('man-of-steel', 'fantasy'),
    ('pacific-rim', 'action'), ('pacific-rim', 'adventure'), ('pacific-rim', 'sci-fi'),
    ('the-matrix-reloaded', 'action'), ('the-matrix-reloaded', 'adventure'), ('the-matrix-reloaded', 'sci-fi'), ('the-matrix-reloaded', 'thriller'),
    ('the-matrix-revolutions', 'action'), ('the-matrix-revolutions', 'adventure'), ('the-matrix-revolutions', 'sci-fi'), ('the-matrix-revolutions', 'thriller'),
    ('dune-part-three', 'action'), ('dune-part-three', 'adventure'), ('dune-part-three', 'sci-fi'), ('dune-part-three', 'drama'),
    ('star-wars-episode-i-the-phantom-menace', 'action'), ('star-wars-episode-i-the-phantom-menace', 'adventure'), ('star-wars-episode-i-the-phantom-menace', 'fantasy'), ('star-wars-episode-i-the-phantom-menace', 'sci-fi'),
    ('star-wars-episode-ii-attack-of-the-clones', 'action'), ('star-wars-episode-ii-attack-of-the-clones', 'adventure'), ('star-wars-episode-ii-attack-of-the-clones', 'fantasy'), ('star-wars-episode-ii-attack-of-the-clones', 'sci-fi'),
    ('star-wars-episode-iv-a-new-hope', 'action'), ('star-wars-episode-iv-a-new-hope', 'adventure'), ('star-wars-episode-iv-a-new-hope', 'fantasy'), ('star-wars-episode-iv-a-new-hope', 'sci-fi'),
    ('star-wars-episode-v-the-empire-strikes-back', 'action'), ('star-wars-episode-v-the-empire-strikes-back', 'adventure'), ('star-wars-episode-v-the-empire-strikes-back', 'fantasy'), ('star-wars-episode-v-the-empire-strikes-back', 'sci-fi'),
    ('star-wars-episode-vi-return-of-the-jedi', 'action'), ('star-wars-episode-vi-return-of-the-jedi', 'adventure'), ('star-wars-episode-vi-return-of-the-jedi', 'fantasy'), ('star-wars-episode-vi-return-of-the-jedi', 'sci-fi'),
    ('godzilla-2014', 'action'), ('godzilla-2014', 'adventure'), ('godzilla-2014', 'sci-fi'), ('godzilla-2014', 'thriller'),
    ('kong-skull-island', 'action'), ('kong-skull-island', 'adventure'), ('kong-skull-island', 'fantasy'), ('kong-skull-island', 'sci-fi'),
    ('godzilla-king-of-the-monsters', 'action'), ('godzilla-king-of-the-monsters', 'adventure'), ('godzilla-king-of-the-monsters', 'fantasy'), ('godzilla-king-of-the-monsters', 'sci-fi'),
    ('godzilla-vs-kong', 'action'), ('godzilla-vs-kong', 'adventure'), ('godzilla-vs-kong', 'sci-fi'),
    ('godzilla-x-kong-the-new-empire', 'action'), ('godzilla-x-kong-the-new-empire', 'adventure'), ('godzilla-x-kong-the-new-empire', 'sci-fi'),
    ('godzilla-x-kong-supernova', 'action'), ('godzilla-x-kong-supernova', 'adventure'), ('godzilla-x-kong-supernova', 'sci-fi')
) AS v(content_slug, genre_slug)
JOIN content c ON c.slug = v.content_slug
JOIN genres g ON g.slug = v.genre_slug
ON CONFLICT DO NOTHING;

-- ─────────────────────────────── people ───────────────────────────────

INSERT INTO people (id, name, slug) VALUES
    ('c0000000-0000-4000-8000-000000000073', 'Christian Bale', 'christian-bale'),
    ('c0000000-0000-4000-8000-000000000074', 'Liam Neeson', 'liam-neeson'),
    ('c0000000-0000-4000-8000-000000000075', 'Katie Holmes', 'katie-holmes'),
    ('c0000000-0000-4000-8000-000000000076', 'Michael Caine', 'michael-caine'),
    ('c0000000-0000-4000-8000-000000000077', 'Heath Ledger', 'heath-ledger'),
    ('c0000000-0000-4000-8000-000000000078', 'Aaron Eckhart', 'aaron-eckhart'),
    ('c0000000-0000-4000-8000-000000000079', 'Gary Oldman', 'gary-oldman'),
    ('c0000000-0000-4000-8000-000000000080', 'Tom Hardy', 'tom-hardy'),
    ('c0000000-0000-4000-8000-000000000081', 'Marion Cotillard', 'marion-cotillard'),
    ('c0000000-0000-4000-8000-000000000082', 'Ben Affleck', 'ben-affleck'),
    ('c0000000-0000-4000-8000-000000000083', 'Henry Cavill', 'henry-cavill'),
    ('c0000000-0000-4000-8000-000000000084', 'Amy Adams', 'amy-adams'),
    ('c0000000-0000-4000-8000-000000000085', 'Jesse Eisenberg', 'jesse-eisenberg'),
    ('c0000000-0000-4000-8000-000000000086', 'Zack Snyder', 'zack-snyder'),
    ('c0000000-0000-4000-8000-000000000087', 'Michael Shannon', 'michael-shannon'),
    ('c0000000-0000-4000-8000-000000000088', 'Kevin Costner', 'kevin-costner'),
    ('c0000000-0000-4000-8000-000000000089', 'Charlie Hunnam', 'charlie-hunnam'),
    ('c0000000-0000-4000-8000-000000000090', 'Idris Elba', 'idris-elba'),
    ('c0000000-0000-4000-8000-000000000091', 'Rinko Kikuchi', 'rinko-kikuchi'),
    ('c0000000-0000-4000-8000-000000000092', 'Guillermo del Toro', 'guillermo-del-toro'),
    ('c0000000-0000-4000-8000-000000000093', 'Keanu Reeves', 'keanu-reeves'),
    ('c0000000-0000-4000-8000-000000000094', 'Laurence Fishburne', 'laurence-fishburne'),
    ('c0000000-0000-4000-8000-000000000095', 'Carrie-Anne Moss', 'carrie-anne-moss'),
    ('c0000000-0000-4000-8000-000000000096', 'Hugo Weaving', 'hugo-weaving'),
    ('c0000000-0000-4000-8000-000000000097', 'Lana Wachowski', 'lana-wachowski'),
    ('c0000000-0000-4000-8000-000000000098', 'Lilly Wachowski', 'lilly-wachowski'),
    ('c0000000-0000-4000-8000-000000000099', 'Jake Lloyd', 'jake-lloyd'),
    ('c0000000-0000-4000-8000-000000000100', 'Samuel L. Jackson', 'samuel-l-jackson'),
    ('c0000000-0000-4000-8000-000000000101', 'Mark Hamill', 'mark-hamill'),
    ('c0000000-0000-4000-8000-000000000102', 'Harrison Ford', 'harrison-ford'),
    ('c0000000-0000-4000-8000-000000000103', 'Carrie Fisher', 'carrie-fisher'),
    ('c0000000-0000-4000-8000-000000000104', 'Alec Guinness', 'alec-guinness'),
    ('c0000000-0000-4000-8000-000000000105', 'Billy Dee Williams', 'billy-dee-williams'),
    ('c0000000-0000-4000-8000-000000000106', 'Irvin Kershner', 'irvin-kershner'),
    ('c0000000-0000-4000-8000-000000000107', 'Ian McDiarmid', 'ian-mcdiarmid'),
    ('c0000000-0000-4000-8000-000000000108', 'Richard Marquand', 'richard-marquand'),
    ('c0000000-0000-4000-8000-000000000109', 'Aaron Taylor-Johnson', 'aaron-taylor-johnson'),
    ('c0000000-0000-4000-8000-000000000110', 'Ken Watanabe', 'ken-watanabe'),
    ('c0000000-0000-4000-8000-000000000111', 'Elizabeth Olsen', 'elizabeth-olsen'),
    ('c0000000-0000-4000-8000-000000000112', 'Bryan Cranston', 'bryan-cranston'),
    ('c0000000-0000-4000-8000-000000000113', 'Gareth Edwards', 'gareth-edwards'),
    ('c0000000-0000-4000-8000-000000000114', 'Tom Hiddleston', 'tom-hiddleston'),
    ('c0000000-0000-4000-8000-000000000115', 'Brie Larson', 'brie-larson'),
    ('c0000000-0000-4000-8000-000000000116', 'John C. Reilly', 'john-c-reilly'),
    ('c0000000-0000-4000-8000-000000000117', 'Jordan Vogt-Roberts', 'jordan-vogt-roberts'),
    ('c0000000-0000-4000-8000-000000000118', 'Kyle Chandler', 'kyle-chandler'),
    ('c0000000-0000-4000-8000-000000000119', 'Vera Farmiga', 'vera-farmiga'),
    ('c0000000-0000-4000-8000-000000000120', 'Millie Bobby Brown', 'millie-bobby-brown'),
    ('c0000000-0000-4000-8000-000000000121', 'Michael Dougherty', 'michael-dougherty'),
    ('c0000000-0000-4000-8000-000000000122', 'Alexander Skarsgård', 'alexander-skarsgard'),
    ('c0000000-0000-4000-8000-000000000123', 'Rebecca Hall', 'rebecca-hall'),
    ('c0000000-0000-4000-8000-000000000124', 'Brian Tyree Henry', 'brian-tyree-henry'),
    ('c0000000-0000-4000-8000-000000000125', 'Adam Wingard', 'adam-wingard'),
    ('c0000000-0000-4000-8000-000000000126', 'Dan Stevens', 'dan-stevens'),
    ('c0000000-0000-4000-8000-000000000127', 'Kaylee Hottle', 'kaylee-hottle'),
    ('c0000000-0000-4000-8000-000000000128', 'Kaitlyn Dever', 'kaitlyn-dever'),
    ('c0000000-0000-4000-8000-000000000129', 'Jack O''Connell', 'jack-oconnell'),
    ('c0000000-0000-4000-8000-000000000130', 'Delroy Lindo', 'delroy-lindo'),
    ('c0000000-0000-4000-8000-000000000131', 'Grant Sputore', 'grant-sputore'),
    ('c0000000-0000-4000-8000-000000000132', 'Cillian Murphy', 'cillian-murphy'),
    ('c0000000-0000-4000-8000-000000000133', 'Emily Blunt', 'emily-blunt'),
    ('c0000000-0000-4000-8000-000000000134', 'Robert Downey Jr.', 'robert-downey-jr'),
    ('c0000000-0000-4000-8000-000000000135', 'Matt Damon', 'matt-damon'),
    ('c0000000-0000-4000-8000-000000000136', 'Charlize Theron', 'charlize-theron'),
    ('c0000000-0000-4000-8000-000000000137', 'Hugh Keays-Byrne', 'hugh-keays-byrne'),
    ('c0000000-0000-4000-8000-000000000138', 'George Miller', 'george-miller')
ON CONFLICT (slug) DO NOTHING;

-- ─────────────────────────────── credits ──────────────────────────────

INSERT INTO credits (content_id, person_id, role, character_name, billing_order)
SELECT c.id, p.id, m.role, m.character_name, m.billing_order
FROM (VALUES
    ('the-dark-knight', 'christian-bale', 'ACTOR', 'Bruce Wayne / Batman', 1),
    ('the-dark-knight', 'heath-ledger', 'ACTOR', 'Joker', 2),
    ('the-dark-knight', 'aaron-eckhart', 'ACTOR', 'Harvey Dent', 3),
    ('the-dark-knight', 'gary-oldman', 'ACTOR', 'James Gordon', 4),
    ('the-dark-knight', 'christopher-nolan', 'DIRECTOR', NULL, 1),

    ('batman-begins', 'christian-bale', 'ACTOR', 'Bruce Wayne / Batman', 1),
    ('batman-begins', 'liam-neeson', 'ACTOR', 'Henri Ducard / Ra''s al Ghul', 2),
    ('batman-begins', 'katie-holmes', 'ACTOR', 'Rachel Dawes', 3),
    ('batman-begins', 'michael-caine', 'ACTOR', 'Alfred Pennyworth', 4),
    ('batman-begins', 'christopher-nolan', 'DIRECTOR', NULL, 1),

    ('the-dark-knight-rises', 'christian-bale', 'ACTOR', 'Bruce Wayne / Batman', 1),
    ('the-dark-knight-rises', 'tom-hardy', 'ACTOR', 'Bane', 2),
    ('the-dark-knight-rises', 'anne-hathaway', 'ACTOR', 'Selina Kyle', 3),
    ('the-dark-knight-rises', 'marion-cotillard', 'ACTOR', 'Miranda Tate', 4),
    ('the-dark-knight-rises', 'christopher-nolan', 'DIRECTOR', NULL, 1),

    ('batman-v-superman-dawn-of-justice', 'ben-affleck', 'ACTOR', 'Bruce Wayne / Batman', 1),
    ('batman-v-superman-dawn-of-justice', 'henry-cavill', 'ACTOR', 'Clark Kent / Superman', 2),
    ('batman-v-superman-dawn-of-justice', 'amy-adams', 'ACTOR', 'Lois Lane', 3),
    ('batman-v-superman-dawn-of-justice', 'jesse-eisenberg', 'ACTOR', 'Lex Luthor', 4),
    ('batman-v-superman-dawn-of-justice', 'zack-snyder', 'DIRECTOR', NULL, 1),

    ('man-of-steel', 'henry-cavill', 'ACTOR', 'Clark Kent / Superman', 1),
    ('man-of-steel', 'amy-adams', 'ACTOR', 'Lois Lane', 2),
    ('man-of-steel', 'michael-shannon', 'ACTOR', 'General Zod', 3),
    ('man-of-steel', 'kevin-costner', 'ACTOR', 'Jonathan Kent', 4),
    ('man-of-steel', 'zack-snyder', 'DIRECTOR', NULL, 1),

    ('pacific-rim', 'charlie-hunnam', 'ACTOR', 'Raleigh Becket', 1),
    ('pacific-rim', 'idris-elba', 'ACTOR', 'Stacker Pentecost', 2),
    ('pacific-rim', 'rinko-kikuchi', 'ACTOR', 'Mako Mori', 3),
    ('pacific-rim', 'guillermo-del-toro', 'DIRECTOR', NULL, 1),

    ('the-matrix', 'keanu-reeves', 'ACTOR', 'Neo', 1),
    ('the-matrix', 'laurence-fishburne', 'ACTOR', 'Morpheus', 2),
    ('the-matrix', 'carrie-anne-moss', 'ACTOR', 'Trinity', 3),
    ('the-matrix', 'hugo-weaving', 'ACTOR', 'Agent Smith', 4),
    ('the-matrix', 'lana-wachowski', 'DIRECTOR', NULL, 1),
    ('the-matrix', 'lilly-wachowski', 'DIRECTOR', NULL, 2),

    ('the-matrix-reloaded', 'keanu-reeves', 'ACTOR', 'Neo', 1),
    ('the-matrix-reloaded', 'laurence-fishburne', 'ACTOR', 'Morpheus', 2),
    ('the-matrix-reloaded', 'carrie-anne-moss', 'ACTOR', 'Trinity', 3),
    ('the-matrix-reloaded', 'hugo-weaving', 'ACTOR', 'Agent Smith', 4),
    ('the-matrix-reloaded', 'lana-wachowski', 'DIRECTOR', NULL, 1),
    ('the-matrix-reloaded', 'lilly-wachowski', 'DIRECTOR', NULL, 2),

    ('the-matrix-revolutions', 'keanu-reeves', 'ACTOR', 'Neo', 1),
    ('the-matrix-revolutions', 'laurence-fishburne', 'ACTOR', 'Morpheus', 2),
    ('the-matrix-revolutions', 'carrie-anne-moss', 'ACTOR', 'Trinity', 3),
    ('the-matrix-revolutions', 'hugo-weaving', 'ACTOR', 'Agent Smith', 4),
    ('the-matrix-revolutions', 'lana-wachowski', 'DIRECTOR', NULL, 1),
    ('the-matrix-revolutions', 'lilly-wachowski', 'DIRECTOR', NULL, 2),

    ('dune-part-three', 'timothee-chalamet', 'ACTOR', 'Paul Atreides', 1),
    ('dune-part-three', 'zendaya', 'ACTOR', 'Chani', 2),
    ('dune-part-three', 'rebecca-ferguson', 'ACTOR', 'Lady Jessica', 3),
    ('dune-part-three', 'denis-villeneuve', 'DIRECTOR', NULL, 1),

    ('star-wars-episode-i-the-phantom-menace', 'liam-neeson', 'ACTOR', 'Qui-Gon Jinn', 1),
    ('star-wars-episode-i-the-phantom-menace', 'ewan-mcgregor', 'ACTOR', 'Obi-Wan Kenobi', 2),
    ('star-wars-episode-i-the-phantom-menace', 'natalie-portman', 'ACTOR', 'Padmé Amidala', 3),
    ('star-wars-episode-i-the-phantom-menace', 'jake-lloyd', 'ACTOR', 'Anakin Skywalker', 4),
    ('star-wars-episode-i-the-phantom-menace', 'george-lucas', 'DIRECTOR', NULL, 1),

    ('star-wars-episode-ii-attack-of-the-clones', 'hayden-christensen', 'ACTOR', 'Anakin Skywalker', 1),
    ('star-wars-episode-ii-attack-of-the-clones', 'natalie-portman', 'ACTOR', 'Padmé Amidala', 2),
    ('star-wars-episode-ii-attack-of-the-clones', 'ewan-mcgregor', 'ACTOR', 'Obi-Wan Kenobi', 3),
    ('star-wars-episode-ii-attack-of-the-clones', 'samuel-l-jackson', 'ACTOR', 'Mace Windu', 4),
    ('star-wars-episode-ii-attack-of-the-clones', 'george-lucas', 'DIRECTOR', NULL, 1),

    ('star-wars-episode-iv-a-new-hope', 'mark-hamill', 'ACTOR', 'Luke Skywalker', 1),
    ('star-wars-episode-iv-a-new-hope', 'harrison-ford', 'ACTOR', 'Han Solo', 2),
    ('star-wars-episode-iv-a-new-hope', 'carrie-fisher', 'ACTOR', 'Princess Leia', 3),
    ('star-wars-episode-iv-a-new-hope', 'alec-guinness', 'ACTOR', 'Obi-Wan Kenobi', 4),
    ('star-wars-episode-iv-a-new-hope', 'george-lucas', 'DIRECTOR', NULL, 1),

    ('star-wars-episode-v-the-empire-strikes-back', 'mark-hamill', 'ACTOR', 'Luke Skywalker', 1),
    ('star-wars-episode-v-the-empire-strikes-back', 'harrison-ford', 'ACTOR', 'Han Solo', 2),
    ('star-wars-episode-v-the-empire-strikes-back', 'carrie-fisher', 'ACTOR', 'Princess Leia', 3),
    ('star-wars-episode-v-the-empire-strikes-back', 'billy-dee-williams', 'ACTOR', 'Lando Calrissian', 4),
    ('star-wars-episode-v-the-empire-strikes-back', 'irvin-kershner', 'DIRECTOR', NULL, 1),

    ('star-wars-episode-vi-return-of-the-jedi', 'mark-hamill', 'ACTOR', 'Luke Skywalker', 1),
    ('star-wars-episode-vi-return-of-the-jedi', 'harrison-ford', 'ACTOR', 'Han Solo', 2),
    ('star-wars-episode-vi-return-of-the-jedi', 'carrie-fisher', 'ACTOR', 'Princess Leia', 3),
    ('star-wars-episode-vi-return-of-the-jedi', 'ian-mcdiarmid', 'ACTOR', 'Emperor Palpatine', 4),
    ('star-wars-episode-vi-return-of-the-jedi', 'richard-marquand', 'DIRECTOR', NULL, 1),

    ('godzilla-2014', 'aaron-taylor-johnson', 'ACTOR', 'Ford Brody', 1),
    ('godzilla-2014', 'ken-watanabe', 'ACTOR', 'Dr. Ishiro Serizawa', 2),
    ('godzilla-2014', 'elizabeth-olsen', 'ACTOR', 'Elle Brody', 3),
    ('godzilla-2014', 'bryan-cranston', 'ACTOR', 'Joe Brody', 4),
    ('godzilla-2014', 'gareth-edwards', 'DIRECTOR', NULL, 1),

    ('kong-skull-island', 'tom-hiddleston', 'ACTOR', 'James Conrad', 1),
    ('kong-skull-island', 'samuel-l-jackson', 'ACTOR', 'Preston Packard', 2),
    ('kong-skull-island', 'brie-larson', 'ACTOR', 'Mason Weaver', 3),
    ('kong-skull-island', 'john-c-reilly', 'ACTOR', 'Hank Marlow', 4),
    ('kong-skull-island', 'jordan-vogt-roberts', 'DIRECTOR', NULL, 1),

    ('godzilla-king-of-the-monsters', 'kyle-chandler', 'ACTOR', 'Mark Russell', 1),
    ('godzilla-king-of-the-monsters', 'vera-farmiga', 'ACTOR', 'Emma Russell', 2),
    ('godzilla-king-of-the-monsters', 'millie-bobby-brown', 'ACTOR', 'Madison Russell', 3),
    ('godzilla-king-of-the-monsters', 'ken-watanabe', 'ACTOR', 'Dr. Ishiro Serizawa', 4),
    ('godzilla-king-of-the-monsters', 'michael-dougherty', 'DIRECTOR', NULL, 1),

    ('godzilla-vs-kong', 'alexander-skarsgard', 'ACTOR', 'Nathan Lind', 1),
    ('godzilla-vs-kong', 'millie-bobby-brown', 'ACTOR', 'Madison Russell', 2),
    ('godzilla-vs-kong', 'rebecca-hall', 'ACTOR', 'Ilene Andrews', 3),
    ('godzilla-vs-kong', 'brian-tyree-henry', 'ACTOR', 'Bernie Hayes', 4),
    ('godzilla-vs-kong', 'adam-wingard', 'DIRECTOR', NULL, 1),

    ('godzilla-x-kong-the-new-empire', 'rebecca-hall', 'ACTOR', 'Ilene Andrews', 1),
    ('godzilla-x-kong-the-new-empire', 'brian-tyree-henry', 'ACTOR', 'Bernie Hayes', 2),
    ('godzilla-x-kong-the-new-empire', 'dan-stevens', 'ACTOR', 'Trapper', 3),
    ('godzilla-x-kong-the-new-empire', 'kaylee-hottle', 'ACTOR', 'Jia', 4),
    ('godzilla-x-kong-the-new-empire', 'adam-wingard', 'DIRECTOR', NULL, 1),

    ('godzilla-x-kong-supernova', 'dan-stevens', 'ACTOR', 'Trapper', 1),
    ('godzilla-x-kong-supernova', 'kaitlyn-dever', 'ACTOR', 'Jesse Portman', 2),
    ('godzilla-x-kong-supernova', 'jack-oconnell', 'ACTOR', 'John Portman', 3),
    ('godzilla-x-kong-supernova', 'delroy-lindo', 'ACTOR', 'Director Conway', 4),
    ('godzilla-x-kong-supernova', 'grant-sputore', 'DIRECTOR', NULL, 1),

    ('oppenheimer', 'cillian-murphy', 'ACTOR', 'J. Robert Oppenheimer', 1),
    ('oppenheimer', 'emily-blunt', 'ACTOR', 'Kitty Oppenheimer', 2),
    ('oppenheimer', 'robert-downey-jr', 'ACTOR', 'Lewis Strauss', 3),
    ('oppenheimer', 'matt-damon', 'ACTOR', 'Leslie Groves', 4),
    ('oppenheimer', 'christopher-nolan', 'DIRECTOR', NULL, 1),

    ('mad-max-fury-road', 'tom-hardy', 'ACTOR', 'Max Rockatansky', 1),
    ('mad-max-fury-road', 'charlize-theron', 'ACTOR', 'Imperator Furiosa', 2),
    ('mad-max-fury-road', 'nicholas-hoult', 'ACTOR', 'Nux', 3),
    ('mad-max-fury-road', 'hugh-keays-byrne', 'ACTOR', 'Immortan Joe', 4),
    ('mad-max-fury-road', 'george-miller', 'DIRECTOR', NULL, 1)
) AS m(content_slug, person_slug, role, character_name, billing_order)
JOIN content c ON c.slug = m.content_slug
JOIN people p ON p.slug = m.person_slug
ON CONFLICT (content_id, person_id, role) DO NOTHING;

-- Raw SQL bypasses CatalogProjector; clear the projection so the startup
-- backfill rebuilds it from the write tables.
DELETE FROM content_read_model;
