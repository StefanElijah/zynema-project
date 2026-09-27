import { useAuth } from 'react-oidc-context';

/**
 * Gate for routes that need a signed-in user.
 *
 * Shows an explicit sign-in panel instead of redirecting automatically: an
 * unexpected redirect loop is much harder to debug than a button.
 */
export default function RequireAuth({ children }) {
  const auth = useAuth();

  if (auth.isLoading) {
    return (
      <div className="flex items-center justify-center py-24 text-white/70">
        Verificando sesión…
      </div>
    );
  }

  if (auth.error) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
        <p className="text-red-400">No se pudo verificar la sesión.</p>
        <p className="max-w-md text-sm text-white/60">{auth.error.message}</p>
        <button
          type="button"
          onClick={() => auth.signinRedirect()}
          className="rounded bg-red-600 px-6 py-2 font-medium text-white hover:bg-red-500"
        >
          Volver a intentar
        </button>
      </div>
    );
  }

  if (!auth.isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
        <h1 className="text-2xl font-semibold">Necesitas iniciar sesión</h1>
        <p className="max-w-md text-sm text-white/60">
          Esta sección requiere una cuenta de Zynema.
        </p>
        <button
          type="button"
          onClick={() => auth.signinRedirect()}
          className="rounded bg-red-600 px-6 py-2 font-medium text-white hover:bg-red-500"
        >
          Iniciar sesión
        </button>
      </div>
    );
  }

  return children;
}
