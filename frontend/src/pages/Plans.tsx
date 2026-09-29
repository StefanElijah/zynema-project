import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { useQueryClient } from '@tanstack/react-query';
import { getAccountQueryKey, useAccount, usePlans, useSubscribe } from '@lib/api';
import { Button } from '../components/ui/button';
import { Skeleton } from '../components/ui/skeleton';

function formatPrice(price?: number, currency?: string, period?: string): string {
  if (price == null) return '—';
  const formatted = new Intl.NumberFormat('es-AR', {
    style: 'currency',
    currency: currency ?? 'EUR',
    minimumFractionDigits: 2,
  }).format(price);
  return period === 'YEARLY' ? `${formatted}/año` : `${formatted}/mes`;
}

/**
 * Pricing and checkout.
 *
 * The idempotency key is generated once per mount and kept for every retry:
 * the key identifies "this intent to subscribe", not the request. Retrying a
 * failed checkout reuses it, so a charge that did happen but whose response
 * was lost comes back as the original subscription instead of a second one.
 */
export default function Plans() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const [idempotencyKey] = useState(() => crypto.randomUUID());

  const plans = usePlans();
  const account = useAccount({ query: { enabled: auth.isAuthenticated } });
  const subscribe = useSubscribe({
    request: { headers: { 'Idempotency-Key': idempotencyKey } },
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getAccountQueryKey() });
      },
    },
  });

  const currentCode = account.data?.entitlements?.planCode;

  const start = (planId?: string) => {
    if (!auth.isAuthenticated) {
      void auth.signinRedirect({ state: { returnTo: '/plans' } });
      return;
    }
    if (planId) {
      subscribe.mutate({ data: { planId, paymentMethod: 'card' } });
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-16">
      <h1 className="text-center text-3xl font-semibold">Elegí tu plan</h1>
      <p className="mt-2 text-center text-sm text-white/60">
        Cancelás cuando quieras. Cambiar de plan es inmediato.
      </p>

      {subscribe.isSuccess && (
        <p className="mx-auto mt-6 max-w-xl rounded border border-emerald-500/40 bg-emerald-500/10 p-3 text-center text-sm text-emerald-200">
          ¡Listo! Tu plan {subscribe.data?.plan?.name ?? ''} está activo.
        </p>
      )}
      {subscribe.isError && (
        <p className="mx-auto mt-6 max-w-xl rounded border border-amber-500/40 bg-amber-500/10 p-3 text-center text-sm text-amber-200">
          {subscribe.error.response?.data?.message ?? 'No pudimos completar la suscripción.'}
        </p>
      )}

      {plans.isLoading && (
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} className="h-64 w-full" />
          ))}
        </div>
      )}

      {plans.isError && (
        <div className="py-20 text-center">
          <p className="text-red-400">No pudimos cargar los planes.</p>
          <Button variant="outline" className="mt-4" onClick={() => void plans.refetch()}>
            Reintentar
          </Button>
        </div>
      )}

      {plans.data && (
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {plans.data.map((plan) => {
            const isCurrent = plan.code != null && plan.code === currentCode;
            return (
              <div
                key={plan.id}
                className={`flex flex-col rounded-xl border p-6 ${
                  isCurrent ? 'border-red-600/60 bg-red-600/5' : 'border-white/10 bg-white/5'
                }`}
              >
                <h2 className="text-lg font-medium">{plan.name}</h2>
                <p className="mt-1 text-3xl font-semibold">
                  {formatPrice(plan.price, plan.currency, plan.billingPeriod)}
                </p>
                <ul className="mt-4 flex-1 space-y-1 text-sm text-white/70">
                  <li>{plan.maxStreams ?? 1} pantalla(s) a la vez</li>
                  <li>Calidad máxima {plan.maxQuality ?? 'HD'}</li>
                  {plan.description && <li>{plan.description}</li>}
                </ul>

                <Button
                  className="mt-6"
                  variant={isCurrent ? 'outline' : 'default'}
                  disabled={isCurrent || subscribe.isPending}
                  onClick={() => start(plan.id)}
                >
                  {isCurrent
                    ? 'Tu plan'
                    : subscribe.isPending
                      ? 'Procesando…'
                      : auth.isAuthenticated
                        ? 'Suscribirme'
                        : 'Iniciar sesión para suscribirme'}
                </Button>
              </div>
            );
          })}
        </div>
      )}

      <p className="mt-10 text-center text-sm text-white/50">
        ¿Ya sos suscriptor?{' '}
        <Link to="/account" className="underline">
          Mirá tu cuenta
        </Link>
      </p>
    </div>
  );
}
