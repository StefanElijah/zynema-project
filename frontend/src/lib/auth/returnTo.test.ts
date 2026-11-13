import { describe, expect, it } from 'vitest';
import { resolveReturnTo, returnToFromLocation } from './returnTo';

describe('OIDC return paths', () => {
  it('preserves the current route, query and hash', () => {
    expect(
      returnToFromLocation({ pathname: '/catalog', search: '?type=SERIES', hash: '#top' })
    ).toBe('/catalog?type=SERIES#top');
  });

  it('uses the internal return path from OIDC state', () => {
    expect(
      resolveReturnTo({ returnTo: '/account?section=profiles' }, 'http://localhost:5173')
    ).toBe('/account?section=profiles');
  });

  it.each([
    undefined,
    null,
    {},
    { returnTo: 'https://evil.example/path' },
    { returnTo: '//evil.example' },
  ])('falls back home for missing or unsafe state (%s)', (state) => {
    expect(resolveReturnTo(state, 'http://localhost:5173')).toBe('/');
  });
});
