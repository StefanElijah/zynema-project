import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { resolveReturnTo } from '../lib/auth/returnTo';

/**
 * Landing point for the OIDC redirect.
 *
 * react-oidc-context processes the authorization code automatically when the
 * provider mounts; this route only exists so the URL resolves to something
 * meaningful while that happens (and is registered as a redirect URI in the
 * Keycloak client).
 */
export default function Callback() {
  const auth = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (auth.isLoading || auth.error) {
      return;
    }

    if (!auth.isAuthenticated) {
      navigate('/', { replace: true });
      return;
    }

    navigate(resolveReturnTo(auth.user?.state), { replace: true });
  }, [auth.error, auth.isAuthenticated, auth.isLoading, auth.user?.state, navigate]);

  if (auth.error) {
    return (
      <div
        role="alert"
        className="flex flex-col items-center justify-center gap-3 py-24 text-center"
      >
        <p className="text-red-400">No se pudo completar el inicio de sesión.</p>
        <p className="max-w-md text-sm text-white/60">{auth.error.message}</p>
        <Link to="/" className="underline">
          Volver al inicio
        </Link>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center py-24 text-white/70">
      Completando inicio de sesión…
    </div>
  );
}
