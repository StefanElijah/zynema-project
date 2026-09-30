-- Fase 5 · CQRS read model for the catalogue (ADR-0006, ADR-0022).
--
-- One row per title, holding everything the read side serves: columns for
-- filtering, sorting and search, plus JSONB payloads ready to return. The write
-- tables (content, seasons, credits, ...) remain the source of truth; a row is
-- rebuilt from them by the projector inside the same transaction as the write,
-- which is why staleness here is measured in milliseconds, not minutes.

CREATE TABLE content_read_model (
    content_id      uuid PRIMARY KEY,
    slug            varchar(255)   NOT NULL UNIQUE,
    type            varchar(20)    NOT NULL,
    status          varchar(20)    NOT NULL,
    title           varchar(255)   NOT NULL,
    release_year    integer,
    maturity_rating varchar(10),
    average_rating  numeric(3, 1),
    popularity      integer,
    -- JSON array of genre slugs: "@> '[\"drama\"]'" uses the GIN index below.
    genre_slugs     jsonb          NOT NULL DEFAULT '[]'::jsonb,
    -- Ready-to-serve payloads (ContentSummaryDto / ContentDetailDto).
    summary         jsonb          NOT NULL,
    detail          jsonb          NOT NULL,
    -- { "<seasonNumber>": [EpisodeDto, ...] }: the key's presence is what
    -- separates "season does not exist" (404) from "season with no episodes".
    episodes        jsonb          NOT NULL DEFAULT '{}'::jsonb,
    search_vector   tsvector       NOT NULL,
    updated_at      timestamptz    NOT NULL DEFAULT now()
);

COMMENT ON TABLE content_read_model IS
    'Denormalised projection of the catalogue. Rebuilt by CatalogProjector from the write tables; never edited by hand.';

CREATE INDEX idx_read_model_type_status ON content_read_model (type, status);
CREATE INDEX idx_read_model_release_year ON content_read_model (release_year);
CREATE INDEX idx_read_model_average_rating ON content_read_model (average_rating);
CREATE INDEX idx_read_model_popularity ON content_read_model (popularity);
CREATE INDEX idx_read_model_genre_slugs ON content_read_model USING gin (genre_slugs);
CREATE INDEX idx_read_model_search ON content_read_model USING gin (search_vector);
