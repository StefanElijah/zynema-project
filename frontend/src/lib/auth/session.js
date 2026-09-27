/**
 * Helpers to read identity information out of an oidc-client-ts user.
 *
 * Keycloak puts realm roles under `realm_access.roles` in the access token.
 * These helpers are the only place that knows about that shape, so components
 * do not have to.
 */

/** Realm roles carried by the access token. */
export function rolesOf(user) {
  const roles = user?.profile?.realm_access?.roles;
  return Array.isArray(roles) ? roles : [];
}

export function hasRole(user, role) {
  return rolesOf(user).includes(role);
}

export function isAdmin(user) {
  return hasRole(user, 'admin');
}

export function isContentManager(user) {
  return hasRole(user, 'admin') || hasRole(user, 'content-manager');
}

/** Display name fallback chain: full name, username, email. */
export function displayNameOf(user) {
  return (
    user?.profile?.name || user?.profile?.preferred_username || user?.profile?.email || 'Usuario'
  );
}

/** Access token of a non-expired session, or null. */
export function accessTokenOf(user) {
  if (!user?.access_token || user.expired) {
    return null;
  }
  return user.access_token;
}
