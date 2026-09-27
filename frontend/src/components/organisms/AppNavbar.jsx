import { Link } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import SearchInput from '../molecules/SearchInput';
import { displayNameOf } from '../../lib/auth/session';

export default function AppNavbar() {
  const auth = useAuth();

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-black/90 backdrop-blur-sm border-b border-white/10">
      <div className="flex items-center justify-between px-4 md:px-8 py-3">
        <Link to="/" className="text-red-600 font-bold text-2xl tracking-tight">
          Zynema
        </Link>

        <nav className="hidden md:flex items-center gap-6 text-sm text-white/80">
          <Link to="/" className="hover:text-white transition">
            Inicio
          </Link>
          <a href="#series" className="hover:text-white transition">
            Series
          </a>
          <a href="#movies" className="hover:text-white transition">
            Películas
          </a>
          <a href="#my-list" className="hover:text-white transition">
            Mi Lista
          </a>
        </nav>

        <div className="flex items-center gap-4">
          <SearchInput />

          {auth.isLoading && <span className="text-xs text-white/50">…</span>}

          {!auth.isLoading && auth.isAuthenticated && (
            <div className="flex items-center gap-3">
              <Link
                to="/account"
                className="text-sm text-white/80 hover:text-white transition"
                title={displayNameOf(auth.user)}
              >
                {displayNameOf(auth.user)}
              </Link>
              <button
                type="button"
                onClick={() => auth.signoutRedirect()}
                className="rounded border border-white/20 px-3 py-1 text-xs text-white/80 hover:border-white/50 hover:text-white transition"
              >
                Salir
              </button>
            </div>
          )}

          {!auth.isLoading && !auth.isAuthenticated && (
            <button
              type="button"
              onClick={() => auth.signinRedirect()}
              className="rounded bg-red-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-red-500 transition"
            >
              Iniciar sesión
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
