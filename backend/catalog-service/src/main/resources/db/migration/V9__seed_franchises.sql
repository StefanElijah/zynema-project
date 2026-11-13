-- ══════════════════════════════════════════════════════════════════════
-- V9__seed_franchises.sql — sagas / franchises
--
-- A franchise is a curated, ordered group of titles (movies and series).
-- Ordering is explicit because release order and in-universe order are not
-- always the same (Star Wars is the obvious example).
-- ══════════════════════════════════════════════════════════════════════

CREATE TABLE franchises (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(120) NOT NULL UNIQUE,
    name        VARCHAR(160) NOT NULL,
    description TEXT,
    cover_url   VARCHAR(500) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE franchise_contents (
    franchise_id UUID    NOT NULL REFERENCES franchises (id) ON DELETE CASCADE,
    content_id   UUID    NOT NULL REFERENCES content (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    PRIMARY KEY (franchise_id, content_id),
    CONSTRAINT uq_franchise_position UNIQUE (franchise_id, position)
);

CREATE INDEX idx_franchise_contents_content ON franchise_contents (content_id);
CREATE INDEX idx_franchises_sort ON franchises (sort_order, name);

INSERT INTO franchises (id, slug, name, description, cover_url, sort_order) VALUES
    ('f0000000-0000-4000-8000-000000000001', 'the-dark-knight-trilogy', 'The Dark Knight Trilogy',
     'Christopher Nolan''s grounded Batman trilogy, from Bruce Wayne''s origin to the final battle for Gotham.',
     '/assets/movies/images/batman_the_dark_knight_trilogy-3840x2160.avif', 1),

    ('f0000000-0000-4000-8000-000000000002', 'star-wars-skywalker-saga', 'Star Wars: Skywalker Saga',
     'The six-film story of Anakin and Luke Skywalker, from the fall of the Republic to the defeat of the Empire.',
     '/assets/movies/images/star_wars_episode_3_revenge_of_the_sith-3840x2160.jpg', 2),

    ('f0000000-0000-4000-8000-000000000003', 'monsterverse', 'MonsterVerse',
     'Legendary''s shared universe of Godzilla, Kong and the Titans, in release order.',
     '/assets/movies/images/godzilla-x-kong-the-new-empire-3840x2160.jpg', 3),

    ('f0000000-0000-4000-8000-000000000004', 'the-matrix', 'The Matrix',
     'The Wachowskis'' science-fiction saga about the war between humanity and the machines.',
     '/assets/movies/images/matrix_1999-3840x2160.avif', 4),

    ('f0000000-0000-4000-8000-000000000005', 'dune', 'Dune',
     'Denis Villeneuve''s adaptation of Frank Herbert''s saga, from Arrakis to the reign of Emperor Paul Atreides.',
     '/assets/movies/images/dune_parte_dos-3840x2160.jpg', 5),

    ('f0000000-0000-4000-8000-000000000006', 'dc-universe', 'DC Universe',
     'A curated selection of DC films from the current catalogue.',
     '/assets/movies/images/superman_2025-3840x2160.jpg', 6),

    ('f0000000-0000-4000-8000-000000000007', 'star-wars-series', 'Star Wars: Series',
     'The television side of the Star Wars galaxy, from the age of the Empire to the New Republic.',
     '/assets/series/images/the-mandalorian-serie-3840x2160.jpg', 7),

    ('f0000000-0000-4000-8000-000000000008', 'alien', 'Alien',
     'The Alien franchise in the catalogue: the Weyland-Yutani era on Earth and in deep space.',
     '/assets/series/images/alien;earth-serie-card.jpg', 8),

    ('f0000000-0000-4000-8000-000000000009', 'predator', 'Predator',
     'The Predator franchise, from the animated hunt to the Badlands.',
     '/assets/movies/images/predator_badlands-3840x2160.jpg', 9),

    ('f0000000-0000-4000-8000-000000000010', 'five-nights-at-freddys', 'Five Nights at Freddy''s',
     'The night-shift horror saga of Freddy Fazbear''s Pizza.',
     '/assets/movies/images/five_nights_at_freddys-3840x2160.jpg', 10),

    ('f0000000-0000-4000-8000-000000000011', 'a-song-of-ice-and-fire', 'A Song of Ice and Fire',
     'The world of Westeros, from the Targaryen civil war to the fight for the Iron Throne.',
     '/assets/series/images/juego_de_tronos-serie-card.jpg', 11),

    ('f0000000-0000-4000-8000-000000000012', 'breaking-bad', 'Breaking Bad',
     'The Albuquerque crime saga of Walter White and Saul Goodman.',
     '/assets/series/images/breaking-bad-serie-3840x2160.jpg', 12),

    ('f0000000-0000-4000-8000-000000000013', 'dc-extended-universe', 'DC Extended Universe',
     'The films that launched the DC Extended Universe: Superman''s arrival and his clash with Batman.',
     '/assets/movies/images/man_of_steel-2013-3840x2160.avif', 13);

INSERT INTO franchise_contents (franchise_id, content_id, position)
SELECT f.id, c.id, v.position
FROM (VALUES
    ('the-dark-knight-trilogy', 'batman-begins', 1),
    ('the-dark-knight-trilogy', 'the-dark-knight', 2),
    ('the-dark-knight-trilogy', 'the-dark-knight-rises', 3),

    ('star-wars-skywalker-saga', 'star-wars-episode-i-the-phantom-menace', 1),
    ('star-wars-skywalker-saga', 'star-wars-episode-ii-attack-of-the-clones', 2),
    ('star-wars-skywalker-saga', 'star-wars-episode-iii-revenge-of-the-sith', 3),
    ('star-wars-skywalker-saga', 'star-wars-episode-iv-a-new-hope', 4),
    ('star-wars-skywalker-saga', 'star-wars-episode-v-the-empire-strikes-back', 5),
    ('star-wars-skywalker-saga', 'star-wars-episode-vi-return-of-the-jedi', 6),

    ('monsterverse', 'godzilla-2014', 1),
    ('monsterverse', 'kong-skull-island', 2),
    ('monsterverse', 'godzilla-king-of-the-monsters', 3),
    ('monsterverse', 'godzilla-vs-kong', 4),
    ('monsterverse', 'godzilla-x-kong-the-new-empire', 5),
    ('monsterverse', 'godzilla-x-kong-supernova', 6),

    ('the-matrix', 'the-matrix', 1),
    ('the-matrix', 'the-matrix-reloaded', 2),
    ('the-matrix', 'the-matrix-revolutions', 3),

    ('dune', 'dune-part-one', 1),
    ('dune', 'dune-part-two', 2),
    ('dune', 'dune-part-three', 3),

    ('dc-universe', 'the-batman', 1),
    ('dc-universe', 'superman-2025', 2),

    ('star-wars-series', 'the-mandalorian', 1),
    ('star-wars-series', 'andor', 2),
    ('star-wars-series', 'star-wars-maul-shadow-lord', 3),

    ('alien', 'alien-romulus', 1),
    ('alien', 'alien-earth', 2),

    ('predator', 'predator-killer-of-killers', 1),
    ('predator', 'predator-badlands', 2),

    ('five-nights-at-freddys', 'five-nights-at-freddys', 1),
    ('five-nights-at-freddys', 'five-nights-at-freddys-2', 2),

    ('a-song-of-ice-and-fire', 'game-of-thrones', 1),
    ('a-song-of-ice-and-fire', 'house-of-the-dragon', 2),

    ('breaking-bad', 'breaking-bad', 1),
    ('breaking-bad', 'better-call-saul', 2),

    ('dc-extended-universe', 'man-of-steel', 1),
    ('dc-extended-universe', 'batman-v-superman-dawn-of-justice', 2)
) AS v(franchise_slug, content_slug, position)
JOIN franchises f ON f.slug = v.franchise_slug
JOIN content c ON c.slug = v.content_slug;
