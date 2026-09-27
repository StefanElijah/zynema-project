-- ══════════════════════════════════════════════════════════════════════
-- V1__init.sql — playback-service schema baseline
--
-- Design notes:
--  * A playback session is volatile state with a lifecycle (heartbeats,
--    concurrency limits, signed URLs in Phase 6). The long-term watch history
--    belongs to user-service: this service keeps the session, the profile and
--    the position, and forwards progress on every heartbeat.
--  * `user_id` is the local account id (same convention as payment), so the
--    events published in Phase 7 carry a stable identifier.
--  * `content_id` / `episode_id` are plain UUIDs: catalog owns that data.
-- ══════════════════════════════════════════════════════════════════════

CREATE TABLE playback_sessions (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL,
    profile_id        UUID        NOT NULL,
    content_id        UUID        NOT NULL,
    episode_id        UUID,
    status            VARCHAR(20) NOT NULL,
    content_title     VARCHAR(255),
    position_seconds  INTEGER     NOT NULL DEFAULT 0,
    duration_seconds  INTEGER,
    device            VARCHAR(60),
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ,
    CONSTRAINT chk_session_status CHECK (status IN ('STARTED', 'PAUSED', 'ENDED')),
    CONSTRAINT chk_session_position CHECK (position_seconds >= 0),
    CONSTRAINT chk_session_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);

-- "Which sessions are open for this account" is the query behind the
-- concurrency limit and the cross-device list.
CREATE INDEX idx_sessions_user_status ON playback_sessions (user_id, status);

CREATE INDEX idx_sessions_profile ON playback_sessions (profile_id, last_heartbeat_at DESC);

-- One open session per (profile, content, episode): resuming the same title on
-- the same profile continues the existing session instead of stacking new ones.
CREATE UNIQUE INDEX uq_session_open
    ON playback_sessions (profile_id, content_id, coalesce(episode_id::text, ''))
    WHERE status <> 'ENDED';
