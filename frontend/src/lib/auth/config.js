/**
 * OIDC settings for the SPA.
 *
 * The frontend is a public client (PKCE, no secret), so everything here is
 * public by definition. Values come from Vite env vars so the same build can
 * point at a local Keycloak or a hosted IdP without code changes.
 */

const DEFAULT_KEYCLOAK_URL = 'http://localhost:8180';
const DEFAULT_REALM = 'zynema';
const DEFAULT_CLIENT_ID = 'zynema-web';

export const CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || DEFAULT_CLIENT_ID;

/** Scopes requested at login. `offline_access` is deliberately not requested. */
export const SCOPE = 'openid profile email';

/**
 * Builds the OIDC authority (issuer) URL from the configured Keycloak base and
 * realm. Kept as a function so tests can exercise the composition without
 * reloading the module.
 */
export function buildAuthority(
  baseUrl = import.meta.env.VITE_KEYCLOAK_URL,
  realm = import.meta.env.VITE_KEYCLOAK_REALM
) {
  const base = (baseUrl || DEFAULT_KEYCLOAK_URL).replace(/\/+$/, '');
  const realmName = realm || DEFAULT_REALM;
  return `${base}/realms/${realmName}`;
}

/** Where Keycloak sends the browser back after login/logout. */
export function buildRedirectUri(origin = window.location.origin) {
  return `${origin.replace(/\/+$/, '')}/callback`;
}

export function buildPostLogoutRedirectUri(origin = window.location.origin) {
  return origin.replace(/\/+$/, '');
}
