-- ══════════════════════════════════════════════════════════════════════
-- V3__link_keycloak_subjects.sql
--
-- Links the seeded local account to its Keycloak counterpart.
--
-- The realm export (infra/keycloak/realm-export/zynema-realm.json) gives the
-- seeded users fixed ids, so the subject a token carries is deterministic and
-- can be seeded here. Without this, the first authenticated call would fall
-- back to email-based linking, which works but is less direct.
-- ══════════════════════════════════════════════════════════════════════

UPDATE users
SET keycloak_subject = '11111111-1111-4111-8111-111111111111'
WHERE email = 'demo@zynema.dev'
  AND keycloak_subject IS NULL;
