-- ══════════════════════════════════════════════════════════════════════
-- V1__init.sql — user-service schema baseline
--
-- Design notes:
--  * `users.id` is our own UUID. The Keycloak subject is stored alongside
--    (`keycloak_subject`) so the identity provider can be swapped or an
--    account can be pre-created before the first login (see ADR-0002).
--  * `content_id` / `episode_id` are plain UUIDs with no foreign key:
--    catalog owns that data, and database-per-service forbids cross-service
--    FKs (ADR-0003).
--  * Watch-history uniqueness needs two partial indexes because PostgreSQL
--    treats NULLs as distinct, which would let the same movie be inserted
--    twice with episode_id = NULL.
-- ══════════════════════════════════════════════════════════════════════

CREATE TABLE users (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_subject   VARCHAR(64)  UNIQUE,
    email              VARCHAR(255) NOT NULL UNIQUE,
    display_name       VARCHAR(120),
    avatar_url         VARCHAR(500),
    preferred_language VARCHAR(10)  NOT NULL DEFAULT 'es',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_keycloak_subject ON users (keycloak_subject);

CREATE TABLE profiles (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(80) NOT NULL,
    avatar_key VARCHAR(40),
    kids       BOOLEAN     NOT NULL DEFAULT false,
    language   VARCHAR(10) NOT NULL DEFAULT 'es',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_profile_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_profiles_user ON profiles (user_id);

CREATE TABLE watchlist (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    content_id UUID        NOT NULL,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_watchlist_profile_content UNIQUE (profile_id, content_id)
);

CREATE INDEX idx_watchlist_profile ON watchlist (profile_id, added_at DESC);

CREATE TABLE watch_history (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id       UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    content_id       UUID        NOT NULL,
    episode_id       UUID,
    position_seconds INTEGER     NOT NULL DEFAULT 0,
    duration_seconds INTEGER,
    completed        BOOLEAN     NOT NULL DEFAULT false,
    last_watched_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_watch_position CHECK (position_seconds >= 0),
    CONSTRAINT chk_watch_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);

CREATE UNIQUE INDEX uq_watch_history_movie
    ON watch_history (profile_id, content_id)
    WHERE episode_id IS NULL;

CREATE UNIQUE INDEX uq_watch_history_episode
    ON watch_history (profile_id, content_id, episode_id)
    WHERE episode_id IS NOT NULL;

CREATE INDEX idx_watch_history_profile_recent ON watch_history (profile_id, last_watched_at DESC);

CREATE INDEX idx_watch_history_continue
    ON watch_history (profile_id, last_watched_at DESC)
    WHERE completed = false;
