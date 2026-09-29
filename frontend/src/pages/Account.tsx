import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';
import {
  getAccountQueryKey,
  getProfilesQueryKey,
  useAccount,
  useCreateProfile,
  useDeleteProfile,
} from '@lib/api';
import { displayNameOf } from '../lib/auth/session';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Skeleton } from '../components/ui/skeleton';

function Row({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="flex justify-between gap-6 border-b border-white/10 py-2 text-sm">
      <span className="text-white/50">{label}</span>
      <span className="text-right font-mono text-white/90">{value ?? '—'}</span>
    </div>
  );
}

/**
 * The account screen: identity from the token, local account and profiles,
 * and the subscription — all from the BFF's `/web/account` view.
 *
 * Profiles are managed here (create and delete); picking "who is watching" is
 * global state and lives in the navbar, not in this page.
 */
export default function Account() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const { data, isLoading, isError, refetch } = useAccount();

  const [name, setName] = useState('');
  const [kids, setKids] = useState(false);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: getProfilesQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getAccountQueryKey() });
  };

  const createProfile = useCreateProfile({ mutation: { onSuccess: invalidate } });
  const deleteProfile = useDeleteProfile({ mutation: { onSuccess: invalidate } });

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) return;
    createProfile.mutate(
      { data: { name: name.trim(), kids, language: 'es' } },
      {
        onSuccess: () => {
          setName('');
          setKids(false);
        },
      }
    );
  };

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="mt-6 h-48 w-full" />
        <Skeleton className="mt-4 h-40 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="py-24 text-center">
        <p className="text-red-400">No se pudo cargar la cuenta.</p>
        <Button variant="outline" className="mt-4" onClick={() => void refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }

  const profiles = data.user?.profiles ?? [];
  const subscription = data.subscription;
  const entitlements = data.entitlements;

  return (
    <div className="mx-auto max-w-3xl px-4 py-16">
      <h1 className="text-3xl font-semibold">Mi cuenta</h1>
      <p className="mt-1 text-sm text-white/60">Sesión de {displayNameOf(auth.user)}</p>

      {(data.degraded?.length ?? 0) > 0 && (
        <p className="mt-4 rounded border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-200">
          Algunas secciones no están disponibles ahora: {data.degraded?.join(', ')}.
        </p>
      )}

      <section className="mt-10 rounded-lg border border-white/10 bg-white/5 p-6">
        <h2 className="mb-4 text-lg font-medium">Identidad</h2>
        <Row label="Usuario" value={data.identity?.username} />
        <Row
          label="Email"
          value={`${data.identity?.email ?? '—'}${data.identity?.emailVerified ? ' (verificado)' : ''}`}
        />
        <Row label="Roles" value={data.identity?.roles?.join(', ')} />
        <Row label="Cuenta" value={data.user?.email} />
        <Row label="Nombre" value={data.user?.displayName} />
        <Row label="Idioma" value={data.user?.preferredLanguage} />
      </section>

      <section className="mt-6 rounded-lg border border-white/10 bg-white/5 p-6">
        <h2 className="mb-4 text-lg font-medium">Suscripción</h2>
        {entitlements?.active ? (
          <>
            <Row label="Plan" value={subscription?.plan?.name ?? entitlements.planCode} />
            <Row label="Estado" value={subscription?.status} />
            <Row
              label="Renueva"
              value={
                subscription?.currentPeriodEnd
                  ? new Date(subscription.currentPeriodEnd).toLocaleDateString('es-AR')
                  : undefined
              }
            />
            <Row label="Calidad máxima" value={entitlements.maxQuality} />
            <Row label="Streams simultáneos" value={entitlements.maxStreams} />
          </>
        ) : (
          <p className="text-sm text-white/70">
            No tenés una suscripción activa.{' '}
            <Link to="/plans" className="underline">
              Ver planes
            </Link>
          </p>
        )}
      </section>

      <section className="mt-6 rounded-lg border border-white/10 bg-white/5 p-6">
        <h2 className="mb-4 text-lg font-medium">Perfiles</h2>

        <ul className="space-y-2">
          {profiles.map((profile) => (
            <li
              key={profile.id}
              className="flex items-center justify-between rounded border border-white/10 px-4 py-2 text-sm"
            >
              <span>
                {profile.name}
                {profile.kids ? ' · infantil' : ''}
              </span>
              <Button
                variant="ghost"
                size="sm"
                disabled={deleteProfile.isPending}
                onClick={() => profile.id && deleteProfile.mutate({ profileId: profile.id })}
              >
                Eliminar
              </Button>
            </li>
          ))}
          {profiles.length === 0 && (
            <li className="text-sm text-white/60">Todavía no hay perfiles.</li>
          )}
        </ul>

        <form onSubmit={submit} className="mt-6 flex flex-wrap items-end gap-3">
          <label className="flex-1 text-sm text-white/70">
            Nuevo perfil
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Nombre"
              maxLength={80}
              className="mt-1"
            />
          </label>
          <label className="flex items-center gap-2 pb-2 text-sm text-white/70">
            <input
              type="checkbox"
              checked={kids}
              onChange={(event) => setKids(event.target.checked)}
              className="accent-red-600"
            />
            Infantil
          </label>
          <Button type="submit" disabled={createProfile.isPending || !name.trim()}>
            {createProfile.isPending ? 'Creando…' : 'Crear'}
          </Button>
        </form>

        {createProfile.error && (
          <p className="mt-3 text-sm text-amber-300">
            {createProfile.error.response?.data?.message ?? 'No se pudo crear el perfil.'}
          </p>
        )}
        {deleteProfile.error && (
          <p className="mt-3 text-sm text-amber-300">
            {deleteProfile.error.response?.data?.message ?? 'No se pudo eliminar el perfil.'}
          </p>
        )}
      </section>
    </div>
  );
}
