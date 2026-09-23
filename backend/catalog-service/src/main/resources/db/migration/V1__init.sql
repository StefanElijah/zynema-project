-- ══════════════════════════════════════════════════════════════════════
-- V1__init.sql — catalog-service schema baseline
--
-- Design notes:
--  * One unified `content` table with a `type` discriminator (MOVIE|SERIES)
--    instead of separate `movies`/`series` tables. Rationale in ADR-0012:
--    one search path, one popularity ranking, one metadata model.
--  * Typed columns for everything we filter/sort/join on; a JSONB `metadata`
--    column for flexible, rarely-queried attributes (awards, budget, tags).
--  * UUID primary keys generated in the database (PostgreSQL 16 has
--    gen_random_uuid() in core; pgcrypto is not required).
--  * Cross-service references (e.g. user watch history) are intentionally
--    absent: catalog does not know about users (database-per-service).
-- ══════════════════════════════════════════════════════════════════════

-- ────────────────────────────── genres ──────────────────────────────

CREATE TABLE genres (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(80)  NOT NULL UNIQUE,
    slug       VARCHAR(80)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ───────────────────────────── content ──────────────────────────────

CREATE TABLE content (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(20)   NOT NULL,
    title           VARCHAR(255)  NOT NULL,
    original_title  VARCHAR(255),
    slug            VARCHAR(255)  NOT NULL UNIQUE,
    synopsis        TEXT,
    tagline         VARCHAR(255),
    release_year    INTEGER,
    maturity_rating VARCHAR(10),
    runtime_minutes INTEGER,
    poster_url      VARCHAR(500),
    backdrop_url    VARCHAR(500),
    trailer_url     VARCHAR(500),
    average_rating  NUMERIC(3, 1),
    popularity      INTEGER       NOT NULL DEFAULT 0,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PUBLISHED',
    metadata        JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_content_type   CHECK (type IN ('MOVIE', 'SERIES')),
    CONSTRAINT chk_content_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_content_rating CHECK (average_rating IS NULL OR (average_rating >= 0 AND average_rating <= 10))
);

CREATE INDEX idx_content_type          ON content (type);
CREATE INDEX idx_content_release_year  ON content (release_year);
CREATE INDEX idx_content_popularity    ON content (popularity DESC);
CREATE INDEX idx_content_rating        ON content (average_rating DESC NULLS LAST);
CREATE INDEX idx_content_metadata_gin  ON content USING gin (metadata jsonb_path_ops);

-- Full-text search over title + original title. 'simple' config keeps the
-- behaviour language-agnostic (titles are proper nouns in many languages).
CREATE INDEX idx_content_title_fts ON content
    USING gin (to_tsvector('simple', title || ' ' || coalesce(original_title, '')));

-- ────────────────────────── content_genres ──────────────────────────

CREATE TABLE content_genres (
    content_id UUID NOT NULL REFERENCES content (id) ON DELETE CASCADE,
    genre_id   UUID NOT NULL REFERENCES genres (id) ON DELETE CASCADE,
    PRIMARY KEY (content_id, genre_id)
);

CREATE INDEX idx_content_genres_genre ON content_genres (genre_id);

-- ────────────────────────────── people ──────────────────────────────

CREATE TABLE people (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ────────────────────────────── credits ─────────────────────────────

CREATE TABLE credits (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id     UUID        NOT NULL REFERENCES content (id) ON DELETE CASCADE,
    person_id      UUID        NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    role           VARCHAR(20) NOT NULL,
    character_name VARCHAR(255),
    billing_order  INTEGER,
    CONSTRAINT chk_credit_role CHECK (role IN ('ACTOR', 'DIRECTOR', 'WRITER', 'PRODUCER', 'COMPOSER')),
    CONSTRAINT uq_credit UNIQUE (content_id, person_id, role)
);

CREATE INDEX idx_credits_content ON credits (content_id, billing_order);
CREATE INDEX idx_credits_person  ON credits (person_id);

-- ────────────────────────────── seasons ─────────────────────────────

CREATE TABLE seasons (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id    UUID         NOT NULL REFERENCES content (id) ON DELETE CASCADE,
    season_number INTEGER      NOT NULL,
    title         VARCHAR(255),
    synopsis      TEXT,
    release_year  INTEGER,
    poster_url    VARCHAR(500),
    CONSTRAINT uq_season_content_number UNIQUE (content_id, season_number)
);

CREATE INDEX idx_seasons_content ON seasons (content_id, season_number);

-- ───────────────────────────── episodes ─────────────────────────────

CREATE TABLE episodes (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id       UUID         NOT NULL REFERENCES seasons (id) ON DELETE CASCADE,
    episode_number  INTEGER      NOT NULL,
    title           VARCHAR(255) NOT NULL,
    synopsis        TEXT,
    runtime_minutes INTEGER,
    release_date    DATE,
    still_url       VARCHAR(500),
    hls_path        VARCHAR(500),
    CONSTRAINT uq_episode_season_number UNIQUE (season_id, episode_number)
);

CREATE INDEX idx_episodes_season ON episodes (season_id, episode_number);
