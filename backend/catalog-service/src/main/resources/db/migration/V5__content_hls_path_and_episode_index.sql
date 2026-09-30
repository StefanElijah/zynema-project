-- Fase 6 · Where a title's HLS ladder lives (ADR-0023, ADR-0024).
--
-- The column holds the object key of the master playlist, not a URL: URLs are
-- signed per session by playback-service, and a stored URL would expire.
-- Unplayable content keeps it NULL, which is exactly how playback knows to
-- answer "not ready" instead of presigning a path that does not exist.

ALTER TABLE content
    ADD COLUMN hls_path varchar(500);

COMMENT ON COLUMN content.hls_path IS
    'Object key of the HLS master playlist (e.g. hls/contents/<id>/master.m3u8); NULL until the video pipeline renders it.';

-- The read model gains a flat episode index: playback resolves an episode by
-- id, and walking the season-keyed payload for one would be a scan in JSON.
ALTER TABLE content_read_model
    ADD COLUMN episodes_by_id jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_read_model_episodes_by_id
    ON content_read_model USING gin (episodes_by_id jsonb_path_ops);
