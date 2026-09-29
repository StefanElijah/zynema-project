/**
 * Helpers to read identity information out of an oidc-client-ts user.
 *
 * Keycloak puts realm roles under `realm_access.roles` in the access token.
 * These helpers are the only place that knows about that shape, so components
 * do not have to.
 *
 * The parameter is structural on purpose: the full `User` from oidc-client-ts
 * satisfies it, and so does a test fixture with only the fields in question —
 * no casts in the tests.
 */

export interface IdentityUser {
  access_token?: string | null;
  expired?: boolean;
  profile?: {
    realm_access?: { roles?: unknown };
    name?: string;
    preferred_username?: string;
    email?: string;
  };
}

/** Realm roles carried by the access token. */
export function rolesOf(user: IdentityUser | null | undefined): string[] {
  const roles = user?.profile?.realm_access?.roles;
  return Array.isArray(roles)
    ? roles.filter((role): role is string => typeof role === 'string')
    : [];
}

export function hasRole(user: IdentityUser | null | undefined, role: string): boolean {
  return rolesOf(user).includes(role);
}

export function isAdmin(user: IdentityUser | null | undefined): boolean {
  return hasRole(user, 'admin');
}

export function isContentManager(user: IdentityUser | null | undefined): boolean {
  return hasRole(user, 'admin') || hasRole(user, 'content-manager');
}

/** Display name fallback chain: full name, username, email. */
export function displayNameOf(user: IdentityUser | null | undefined): string {
  return (
    user?.profile?.name || user?.profile?.preferred_username || user?.profile?.email || 'Usuario'
  );
}

/** Access token of a non-expired session, or null. */
export function accessTokenOf(user: IdentityUser | null | undefined): string | null {
  if (!user?.access_token || user.expired) {
    return null;
  }
  return user.access_token;
}
