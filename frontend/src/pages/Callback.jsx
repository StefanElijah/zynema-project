/**
 * Landing point for the OIDC redirect.
 *
 * react-oidc-context processes the authorization code automatically when the
 * provider mounts; this route only exists so the URL resolves to something
 * meaningful while that happens (and is registered as a redirect URI in the
 * Keycloak client).
 */
export default function Callback() {
  return (
    <div className="flex items-center justify-center py-24 text-white/70">
      Completando inicio de sesión…
    </div>
  );
}
