/**
 * The payloads the SPA reads today, typed by hand.
 *
 * Hand-written on purpose in this step: Fase 8's next commit generates the BFF
 * client (orval) from its OpenAPI spec, and these interfaces are what the
 * generated types replace — for the BFF endpoints. Domain payloads that never
 * pass through the BFF (none, after that commit) keep living here.
 */

/** `GET /auth/me` (auth-service through the gateway). */
export interface AuthMe {
  subject: string;
  username?: string | null;
  email?: string | null;
  emailVerified?: boolean;
  authorities?: string[];
  issuer?: string;
  expiresAt?: string;
}

/** `GET /users/me` (user-service through the gateway). */
export interface UserAccount {
  id: string;
  email: string;
  displayName: string;
  preferredLanguage?: string | null;
  profiles?: UserProfile[];
}

export interface UserProfile {
  id: string;
  name: string;
  kids?: boolean;
}

/** `GET /api/v1/web/catalog/{idOrSlug}`: the subset the player uses. */
export interface ContentView {
  content?: {
    id: string;
    type: string;
    title: string;
    slug?: string;
    releaseYear?: number | null;
    synopsis?: string | null;
  };
  playback?: {
    allowed: boolean;
    reason?: 'AUTHENTICATION_REQUIRED' | 'SUBSCRIPTION_REQUIRED' | 'UNAVAILABLE' | null;
  };
}

/** `GET /api/v1/web/account`: the subset the player uses to pick a profile. */
export interface AccountView {
  user?: {
    profiles?: UserProfile[];
  };
}

/** `POST /api/v1/playback/sessions` response, as the player consumes it. */
export interface PlaybackSession {
  id: string;
  profileId: string;
  contentId: string;
  contentTitle?: string | null;
  streamPath: string;
  positionSeconds?: number | null;
  durationSeconds?: number | null;
}
