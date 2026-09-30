import { describe, expect, it } from 'vitest';
import {
  accessTokenOf,
  displayNameOf,
  hasRole,
  isContentManager,
  rolesOf,
  type IdentityUser,
} from './session';

const user = (
  profile: IdentityUser['profile'],
  accessToken: string | null = 'token',
  expired = false
): IdentityUser => ({
  access_token: accessToken,
  expired,
  profile,
});

describe('session helpers', () => {
  it('reads Keycloak realm roles from the access token profile', () => {
    const current = user({ realm_access: { roles: ['user', 'content-manager'] } });

    expect(rolesOf(current)).toEqual(['user', 'content-manager']);
    expect(hasRole(current, 'content-manager')).toBe(true);
    expect(hasRole(current, 'admin')).toBe(false);
  });

  it('treats missing or malformed roles as no roles', () => {
    expect(rolesOf(user({}))).toEqual([]);
    expect(rolesOf(user({ realm_access: { roles: 'user' } }))).toEqual([]);
    expect(rolesOf(undefined)).toEqual([]);
  });

  it('admin implies content management', () => {
    expect(isContentManager(user({ realm_access: { roles: ['admin'] } }))).toBe(true);
    expect(isContentManager(user({ realm_access: { roles: ['user'] } }))).toBe(false);
  });

  it('prefers full name, then username, then email', () => {
    expect(displayNameOf(user({ name: 'Demo User', preferred_username: 'demo' }))).toBe(
      'Demo User'
    );
    expect(displayNameOf(user({ preferred_username: 'demo' }))).toBe('demo');
    expect(displayNameOf(user({ email: 'demo@zynema.dev' }))).toBe('demo@zynema.dev');
    expect(displayNameOf(user({}))).toBe('Usuario');
  });

  it('returns the access token only for a live session', () => {
    expect(accessTokenOf(user({}, 'abc'))).toBe('abc');
    expect(accessTokenOf(user({}, 'abc', true))).toBeNull();
    expect(accessTokenOf(user({}, null))).toBeNull();
    expect(accessTokenOf(undefined)).toBeNull();
  });
});
