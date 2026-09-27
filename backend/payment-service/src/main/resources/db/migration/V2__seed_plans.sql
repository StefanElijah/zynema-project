-- ══════════════════════════════════════════════════════════════════════
-- V2__seed_plans.sql — the three subscription tiers
--
-- Deterministic ids so tests and the frontend can rely on them:
--   81000000-0000-4000-8000-00000000000N
-- max_streams is what playback-service enforces as concurrent sessions.
-- ══════════════════════════════════════════════════════════════════════

INSERT INTO plans (id, code, name, description, price, currency, billing_period,
                   max_streams, max_quality, features)
VALUES
    ('81000000-0000-4000-8000-000000000001', 'basic', 'Basic',
     'One screen, standard definition.',
     6.99, 'USD', 'MONTHLY', 1, 'SD',
     '{"ads": true, "downloads": false, "simultaneous_devices": 1}'),

    ('81000000-0000-4000-8000-000000000002', 'standard', 'Standard',
     'Two screens at the same time, full HD.',
     12.99, 'USD', 'MONTHLY', 2, 'FHD',
     '{"ads": false, "downloads": true, "simultaneous_devices": 2}'),

    ('81000000-0000-4000-8000-000000000003', 'premium', 'Premium',
     'Four screens, 4K, spatial audio.',
     19.99, 'USD', 'MONTHLY', 4, 'UHD',
     '{"ads": false, "downloads": true, "simultaneous_devices": 4, "spatial_audio": true}');
