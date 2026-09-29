import { Link } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { useQueryClient } from '@tanstack/react-query';
import { Check, LogOut, UserRound } from 'lucide-react';
import SearchInput from '../molecules/SearchInput';
import { displayNameOf } from '../../lib/auth/session';
import { useSelectedProfile } from '../../hooks/useSelectedProfile';
import { useProfileStore } from '../../stores/useProfileStore';
import { Avatar, AvatarFallback } from '../ui/avatar';
import { Button } from '../ui/button';
import { Skeleton } from '../ui/skeleton';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu';

function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

export default function AppNavbar() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const { profiles, selected } = useSelectedProfile();
  const select = useProfileStore((state) => state.select);
  const clear = useProfileStore((state) => state.clear);

  const signOut = () => {
    // The next account must not inherit who was watching, nor see the
    // previous account's cached data.
    clear();
    queryClient.clear();
    void auth.signoutRedirect();
  };

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

          {auth.isLoading && <Skeleton className="size-9 rounded-full" />}

          {!auth.isLoading && auth.isAuthenticated && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="rounded-full" aria-label="Perfil">
                  <Avatar>
                    <AvatarFallback>
                      {initialsOf(selected?.name ?? displayNameOf(auth.user))}
                    </AvatarFallback>
                  </Avatar>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="min-w-52">
                <DropdownMenuLabel>
                  {selected ? `Viendo como ${selected.name}` : displayNameOf(auth.user)}
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                {profiles.map((profile) => (
                  <DropdownMenuItem
                    key={profile.id}
                    onSelect={() => {
                      if (profile.id) select(profile.id);
                    }}
                  >
                    <Check className={profile.id === selected?.id ? 'opacity-100' : 'opacity-0'} />
                    {profile.name}
                  </DropdownMenuItem>
                ))}
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link to="/account">
                    <UserRound />
                    Mi cuenta
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={signOut}>
                  <LogOut />
                  Salir
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}

          {!auth.isLoading && !auth.isAuthenticated && (
            <Button onClick={() => void auth.signinRedirect()}>Iniciar sesión</Button>
          )}
        </div>
      </div>
    </header>
  );
}
