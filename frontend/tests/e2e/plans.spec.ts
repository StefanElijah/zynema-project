import { expect, test } from '@playwright/test';

const PLANS = [
  {
    id: '81000000-0000-4000-8000-000000000001',
    code: 'basic',
    name: 'Basic',
    description: 'Una pantalla, HD',
    price: 4.99,
    currency: 'EUR',
    billingPeriod: 'MONTHLY',
    maxStreams: 1,
    maxQuality: 'HD',
  },
  {
    id: '81000000-0000-4000-8000-000000000002',
    code: 'standard',
    name: 'Standard',
    description: 'Dos pantallas, Full HD',
    price: 9.99,
    currency: 'EUR',
    billingPeriod: 'MONTHLY',
    maxStreams: 2,
    maxQuality: 'FHD',
  },
];

test('the pricing page shows the plans and asks visitors to sign in', async ({ page }) => {
  await page.route('**/api/v1/web/plans', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(PLANS) })
  );

  await page.goto('/plans');

  await expect(page.getByRole('heading', { name: 'Elegí tu plan' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Basic' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Standard' })).toBeVisible();
  await expect(page.getByText('1 pantalla(s) a la vez')).toBeVisible();

  // No session: the CTA is the sign-in, not a checkout that would 401.
  await expect(
    page.getByRole('button', { name: 'Iniciar sesión para suscribirme' }).first()
  ).toBeVisible();
});
