import { describe, expect, it } from 'vitest';
import { buildAuthority, buildPostLogoutRedirectUri, buildRedirectUri } from './config';

describe('auth config', () => {
  it('builds the authority from base URL and realm', () => {
    expect(buildAuthority('http://localhost:8180', 'zynema')).toBe(
      'http://localhost:8180/realms/zynema'
    );
  });

  it('trims trailing slashes so the URL is not doubled up', () => {
    expect(buildAuthority('http://localhost:8180/', 'zynema/')).toBe(
      'http://localhost:8180/realms/zynema/'
    );
  });

  it('falls back to the local Keycloak when nothing is configured', () => {
    expect(buildAuthority(undefined, undefined)).toBe('http://localhost:8180/realms/zynema');
  });

  it('exposes a callback redirect URI on the current origin', () => {
    expect(buildRedirectUri('http://localhost:5173')).toBe('http://localhost:5173/callback');
  });

  it('uses the origin as the post-logout destination', () => {
    expect(buildPostLogoutRedirectUri('http://localhost:5173/')).toBe('http://localhost:5173');
  });
});
