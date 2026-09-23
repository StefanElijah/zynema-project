-- ══════════════════════════════════════════════════════════════════════
-- V2__seed_users.sql — demo account with two profiles
--
-- Deterministic UUIDs so the frontend and tests can rely on stable ids:
--   71000000-… users  ·  72000000-… profiles
-- The catalog content ids referenced in the watchlist/history are the ones
-- seeded by catalog-service (Dune: Part Two, Arcane, Interstellar).
-- ══════════════════════════════════════════════════════════════════════

INSERT INTO users (id, email, display_name, avatar_url, preferred_language) VALUES
    ('71000000-0000-4000-8000-000000000001', 'demo@zynema.dev', 'Demo User', NULL, 'es');

INSERT INTO profiles (id, user_id, name, avatar_key, kids, language) VALUES
    ('72000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000001', 'Demo',  'avatar-01', false, 'es'),
    ('72000000-0000-4000-8000-000000000002', '71000000-0000-4000-8000-000000000001', 'Kids',  'avatar-07', true,  'es');

INSERT INTO watchlist (profile_id, content_id, added_at) VALUES
    ('72000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000003', now() - interval '3 days'),
    ('72000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000008', now() - interval '2 days'),
    ('72000000-0000-4000-8000-000000000001', 'a2000000-0000-4000-8000-000000000002', now() - interval '1 day');

INSERT INTO watch_history (profile_id, content_id, episode_id, position_seconds, duration_seconds, completed, last_watched_at) VALUES
    ('72000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000003', NULL,
     5400, 9960, false, now() - interval '2 hours'),
    ('72000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000008', NULL,
     10140, 10140, true, now() - interval '5 days'),
    ('72000000-0000-4000-8000-000000000001', 'a2000000-0000-4000-8000-000000000002', 'e1000000-0000-4000-8000-000000000002',
     1200, 2400, false, now() - interval '1 hour');
