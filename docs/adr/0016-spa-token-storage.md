# ADR-0016: Where the SPA keeps its tokens

- **Status:** Accepted
- **Date:** 2026-09-26
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 3 (authentication)

## Context

The frontend is an OIDC public client using authorization code + PKCE. It ends
up holding an access token and a refresh token in the browser. Three common
options:

1. **`localStorage`** — survives restarts and tab closes, but any script running
   on the page (including a compromised dependency) can read it at any time. It
   is the option most often exploited in real incidents.
2. **`sessionStorage`** — cleared when the tab closes, and only readable by the
   origin that wrote it. Still readable by injected scripts, but the exposure
   window is bounded by the session.
3. **BFF-held tokens (cookie session)** — the browser never sees a token; the
   BFF keeps it server-side and authenticates the SPA with an `HttpOnly`,
   `Secure`, `SameSite` cookie. The strongest option, and the industry direction
   for SPAs (the "token handler" / BFF pattern). It requires the BFF to proxy
   every call, plus CSRF protection, and it moves the token out of the client
   that actually talks to the APIs.

## Decision

For this phase the SPA keeps tokens in **`sessionStorage`**, with:

- **Short-lived access tokens** (15 minutes) and **refresh tokens with
  rotation** enabled in the realm.
- **Silent renew** through the refresh token, so the user is not bounced to
  Keycloak every fifteen minutes. No third-party-cookie iframe.
- The axios client attaches the token and, on a 401, attempts exactly **one**
  silent renew before sending the user to the login flow. A renewed token that
  is rejected again does _not_ loop back to login.
- `post_logout_redirect_uri` and the logout flow are wired so the Keycloak
  session is closed too, not only the local one.

The BFF-held-token variant is the documented alternative for Fase 5, where the
BFF becomes the SPA's API. At that point the migration is contained: the SPA
stops holding tokens, the BFF holds them, and the axios interceptor is replaced
by cookie-based calls.

## Consequences

**Positive**

- Simple, standard, and easy to debug: the token is visible in the browser's
  session storage and in the Swagger UI "authorize" box.
- No dependency on third-party cookies, which browsers now block.
- Closing the tab ends the client-side session.

**Negative**

- An XSS vulnerability can still read the token during the session. This is
  accepted for the current phase and mitigated by CSP headers, by keeping the
  dependency surface small, and by short token lifetimes.
- No cross-tab session sharing: sessionStorage is per tab.

## Notes

- The API is not vulnerable to CSRF in this design because it does not use
  cookies; the bearer token is attached explicitly by the client.
- Moving to the BFF pattern also removes the SPA as an OAuth client, which
  simplifies the realm (a single confidential client replaces the public one).
