-- ══════════════════════════════════════════════════════════════════════
-- V7__seed_trailers.sql — official trailers for the whole catalogue
--
-- Every title points at its official trailer on YouTube. The URLs were
-- checked against YouTube's oEmbed endpoint (title + official channel).
--
-- The detail view is served from content_read_model, so the projection is
-- patched in the same migration: a plain UPDATE on content would leave the
-- read side (and its cache) serving null until the next content write.
-- ══════════════════════════════════════════════════════════════════════

CREATE TEMP TABLE seed_trailers (slug TEXT PRIMARY KEY, url TEXT NOT NULL);

INSERT INTO seed_trailers (slug, url) VALUES
    ('alien-earth', 'https://www.youtube.com/watch?v=ZbsiKjVAV28'),
    ('alien-romulus', 'https://www.youtube.com/watch?v=OzY2r2JXsDM'),
    ('andor', 'https://www.youtube.com/watch?v=cKOegEuCcfw'),
    ('arcane', 'https://www.youtube.com/watch?v=fXmAurh012s'),
    ('arrival', 'https://www.youtube.com/watch?v=tFMo3UJ4B4g'),
    ('avatar-the-way-of-water', 'https://www.youtube.com/watch?v=d9MyW72ELq0'),
    ('better-call-saul', 'https://www.youtube.com/watch?v=HN4oydykJFc'),
    ('blade-runner-2049', 'https://www.youtube.com/watch?v=gCcx85zbxz4'),
    ('breaking-bad', 'https://www.youtube.com/watch?v=XZ8daibM3AE'),
    ('chernobyl', 'https://www.youtube.com/watch?v=s9APLXM9Ei8'),
    ('daredevil-born-again', 'https://www.youtube.com/watch?v=7xALolZzhSM'),
    ('dark', 'https://www.youtube.com/watch?v=ESEUoa-mz2c'),
    ('dexter-resurrection', 'https://www.youtube.com/watch?v=84o1Q6fB20k'),
    ('dune-part-one', 'https://www.youtube.com/watch?v=n9xhJrPXop4'),
    ('dune-part-two', 'https://www.youtube.com/watch?v=Way9Dexny3w'),
    ('everything-everywhere-all-at-once', 'https://www.youtube.com/watch?v=wxN1T1uxQ2g'),
    ('f1-the-movie', 'https://www.youtube.com/watch?v=8yh9BPUBbbQ'),
    ('fallout', 'https://www.youtube.com/watch?v=0kQ8i2FpRDk'),
    ('five-nights-at-freddys', 'https://www.youtube.com/watch?v=0VH9WCFV6XQ'),
    ('five-nights-at-freddys-2', 'https://www.youtube.com/watch?v=dSDpoobO6yM'),
    ('foundation', 'https://www.youtube.com/watch?v=X4QYV5GTz7c'),
    ('game-of-thrones', 'https://www.youtube.com/watch?v=KPLWWIOCOOQ'),
    ('gladiator', 'https://www.youtube.com/watch?v=P5ieIbInFpg'),
    ('house-of-the-dragon', 'https://www.youtube.com/watch?v=DotnJ7tTA34'),
    ('inception', 'https://www.youtube.com/watch?v=Qwe6qXFTdgc'),
    ('interstellar', 'https://www.youtube.com/watch?v=zSWdZVtXT7E'),
    ('invincible', 'https://www.youtube.com/watch?v=-bfAVpuko5o'),
    ('mad-max-fury-road', 'https://www.youtube.com/watch?v=hEJnMQG9ev8'),
    ('oppenheimer', 'https://www.youtube.com/watch?v=bK6ldnjE3Y0'),
    ('parasite', 'https://www.youtube.com/watch?v=isOGD_7hNIY'),
    ('peaky-blinders', 'https://www.youtube.com/watch?v=EM12mcTEI88'),
    ('predator-badlands', 'https://www.youtube.com/watch?v=43R9l7EkJwE'),
    ('predator-killer-of-killers', 'https://www.youtube.com/watch?v=fbddYji1F8s'),
    ('project-hail-mary', 'https://www.youtube.com/watch?v=m08TxIsFTRI'),
    ('severance', 'https://www.youtube.com/watch?v=xEQP4VVuyrY'),
    ('sicario', 'https://www.youtube.com/watch?v=Yfhu5JIxnZc'),
    ('spider-man-across-the-spider-verse', 'https://www.youtube.com/watch?v=shW9i6k8cB0'),
    ('star-wars-episode-iii-revenge-of-the-sith', 'https://www.youtube.com/watch?v=5UnjrG_N8hU'),
    ('star-wars-maul-shadow-lord', 'https://www.youtube.com/watch?v=DkVepshZhGc'),
    ('stranger-things', 'https://www.youtube.com/watch?v=b9EkMc79ZSU'),
    ('superman-2025', 'https://www.youtube.com/watch?v=Ox8ZLF6cGM0'),
    ('the-batman', 'https://www.youtube.com/watch?v=mqqft2x_Aa4'),
    ('the-bear', 'https://www.youtube.com/watch?v=ojjCvICC86c'),
    ('the-boys', 'https://www.youtube.com/watch?v=M1bhOaLV4FU'),
    ('the-dark-knight', 'https://www.youtube.com/watch?v=TQfATDZY5Y4'),
    ('the-last-of-us', 'https://www.youtube.com/watch?v=uLtkt8BonwM'),
    ('the-mandalorian', 'https://www.youtube.com/watch?v=aOC8E8z_ifw'),
    ('the-matrix', 'https://www.youtube.com/watch?v=tGgCqGm_6Hs'),
    ('the-walking-dead', 'https://www.youtube.com/watch?v=3PJgm8_ODuU'),
    ('tron-ares', 'https://www.youtube.com/watch?v=YShVEXb7-ic'),
    ('westworld', 'https://www.youtube.com/watch?v=kEkZdgWu7mM'),
    ('whiplash', 'https://www.youtube.com/watch?v=Df1xkYYbYrY');

UPDATE content AS c
SET trailer_url = t.url
FROM seed_trailers AS t
WHERE c.slug = t.slug;

UPDATE content_read_model AS rm
SET detail = jsonb_set(rm.detail, '{trailerUrl}', to_jsonb(t.url)),
    updated_at = now()
FROM seed_trailers AS t
WHERE rm.slug = t.slug;

DROP TABLE seed_trailers;
