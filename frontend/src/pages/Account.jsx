import { useQuery } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';
import { apiClient } from '../lib/api/client';
import { displayNameOf, rolesOf } from '../lib/auth/session';

function Row({ label, value }) {
  return (
    <div className="flex justify-between gap-6 border-b border-white/10 py-2 text-sm">
      <span className="text-white/50">{label}</span>
      <span className="text-right font-mono text-white/90">{value ?? '—'}</span>
    </div>
  );
}

export default function Account() {
  const auth = useAuth();

  const authMe = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: async () => (await apiClient.get('/auth/me')).data,
  });

  const account = useQuery({
    queryKey: ['users', 'me'],
    queryFn: async () => (await apiClient.get('/users/me')).data,
  });

  if (authMe.isLoading || account.isLoading) {
    return <div className="py-24 text-center text-white/70">Cargando tu cuenta…</div>;
  }

  if (authMe.isError || account.isError) {
    return (
      <div className="py-24 text-center">
        <p className="text-red-400">No se pudo cargar la cuenta.</p>
        <p className="mt-2 text-sm text-white/60">
          {authMe.error?.message || account.error?.message}
        </p>
      </div>
    );
  }

  const session = authMe.data;
  const profile = account.data;
  const roles = rolesOf(auth.user);

  return (
    <div className="mx-auto max-w-3xl px-4 py-16">
      <h1 className="text-3xl font-semibold">Mi cuenta</h1>
      <p className="mt-1 text-sm text-white/60">Sesión de {displayNameOf(auth.user)}</p>

      <section className="mt-10 rounded-lg border border-white/10 bg-white/5 p-6">
        <h2 className="mb-4 text-lg font-medium">Identidad (Keycloak)</h2>
        <Row label="Sujeto" value={session.subject} />
        <Row label="Usuario" value={session.username} />
        <Row
          label="Email"
          value={`${session.email ?? '—'}${session.emailVerified ? ' (verificado)' : ''}`}
        />
        <Row label="Roles" value={roles.join(', ') || '—'} />
        <Row label="Autoridades" value={session.authorities?.join(', ')} />
        <Row label="Emisor" value={session.issuer} />
        <Row label="Token expira" value={session.expiresAt} />
      </section>

      <section className="mt-6 rounded-lg border border-white/10 bg-white/5 p-6">
        <h2 className="mb-4 text-lg font-medium">Cuenta local (user-service)</h2>
        <Row label="Id" value={profile.id} />
        <Row label="Email" value={profile.email} />
        <Row label="Nombre" value={profile.displayName} />
        <Row label="Idioma" value={profile.preferredLanguage} />
        <Row label="Perfiles" value={profile.profiles?.length ?? 0} />
        {profile.profiles?.length > 0 && (
          <ul className="mt-4 flex flex-wrap gap-2">
            {profile.profiles.map((item) => (
              <li
                key={item.id}
                className="rounded-full border border-white/15 px-3 py-1 text-xs text-white/80"
              >
                {item.name}
                {item.kids ? ' · infantil' : ''}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
