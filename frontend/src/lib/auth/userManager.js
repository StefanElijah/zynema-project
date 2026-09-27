import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
import {
  buildAuthority,
  buildPostLogoutRedirectUri,
  buildRedirectUri,
  CLIENT_ID,
  SCOPE,
} from './config';

/**
 * Single UserManager instance shared by React (through AuthProvider) and by
 * the axios client, which needs the current access token outside the React
 * tree. Creating it once avoids two instances fighting over the same storage.
 *
 * Tokens live in sessionStorage: they are gone when the tab closes, and the
 * short access-token lifespan keeps the exposure window small. The
 * BFF-with-cookies alternative is documented in ADR-0016.
 */
export const userManager = new UserManager({
  authority: buildAuthority(),
  client_id: CLIENT_ID,
  redirect_uri: buildRedirectUri(),
  post_logout_redirect_uri: buildPostLogoutRedirectUri(),
  scope: SCOPE,
  automaticSilentRenew: true,
  // Cross-tab session monitoring relies on a third-party-cookie iframe, which
  // modern browsers block. Refresh tokens make it unnecessary.
  monitorSession: false,
  loadUserInfo: false,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
});
