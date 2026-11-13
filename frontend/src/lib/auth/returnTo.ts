export interface ReturnLocation {
  pathname: string;
  search?: string;
  hash?: string;
}

/** Keeps a deep link, including its query string and hash, across OIDC login. */
export function returnToFromLocation(location: ReturnLocation): string {
  return `${location.pathname}${location.search ?? ''}${location.hash ?? ''}`;
}

/** Accepts only same-origin app paths from OIDC state; missing/unsafe state goes home. */
export function resolveReturnTo(state: unknown, currentOrigin = window.location.origin): string {
  if (typeof state !== 'object' || state === null || Array.isArray(state)) {
    return '/';
  }

  const candidate = (state as { returnTo?: unknown }).returnTo;
  if (typeof candidate !== 'string' || !candidate.startsWith('/') || candidate.startsWith('//')) {
    return '/';
  }

  try {
    const origin = new URL(currentOrigin).origin;
    const target = new URL(candidate, origin);
    if (target.origin !== origin) {
      return '/';
    }
    return `${target.pathname}${target.search}${target.hash}`;
  } catch {
    return '/';
  }
}
