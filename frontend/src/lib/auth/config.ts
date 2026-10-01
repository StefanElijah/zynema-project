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

/**
 * Strips trailing slashes. A loop instead of `/\/+$/`: the regex is linear in
 * practice but Sonar flags the shape, and the intent here is clearer as code.
 */
function trimTrailingSlashes(value: string): string {
  let end = value.length;
  while (end > 0 && value.charAt(end - 1) === '/') {
    end -= 1;
  }
  return value.slice(0, end);
}

export const CLIENT_ID: string = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || DEFAULT_CLIENT_ID;

/** Scopes requested at login. `offline_access` is deliberately not requested. */
export const SCOPE = 'openid profile email';

/**
 * Builds the OIDC authority (issuer) URL from the configured Keycloak base and
 * realm. Kept as a function so tests can exercise the composition without
 * reloading the module.
 */
export function buildAuthority(
  baseUrl: string | undefined = import.meta.env.VITE_KEYCLOAK_URL,
  realm: string | undefined = import.meta.env.VITE_KEYCLOAK_REALM
): string {
  const base = trimTrailingSlashes(baseUrl || DEFAULT_KEYCLOAK_URL);
  const realmName = realm || DEFAULT_REALM;
  return `${base}/realms/${realmName}`;
}

/** Where Keycloak sends the browser back after login/logout. */
export function buildRedirectUri(origin: string = window.location.origin): string {
  return `${trimTrailingSlashes(origin)}/callback`;
}

export function buildPostLogoutRedirectUri(origin: string = window.location.origin): string {
  return trimTrailingSlashes(origin);
}
