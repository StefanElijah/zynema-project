import { test, expect } from '@playwright/test';

/**
 * Browser-level authentication flow.
 *
 * Gated behind E2E_AUTH=1 because it needs the whole stack running:
 * Keycloak (docker compose --profile auth up -d), the gateway, auth-service
 * and user-service. Without the flag the suite is skipped, so `pnpm test:e2e`
 * stays green for a frontend-only checkout.
 *
 * The API-level equivalent runs in the backend test suite and in the
 * verification runbook (password grant through the gateway).
 */
const authEnabled = process.env.E2E_AUTH === '1';

test.describe('authentication', () => {
  test.skip(!authEnabled, 'Set E2E_AUTH=1 with Keycloak and the services running');

  test('a visitor signs in through Keycloak and lands on their account', async ({ page }) => {
    await page.goto('/account');

    await expect(page.getByRole('heading', { name: 'Necesitas iniciar sesión' })).toBeVisible();
    await page.getByRole('button', { name: 'Iniciar sesión' }).click();

    // Keycloak login form
    await page.waitForURL(/\/realms\/zynema\/protocol\/openid-connect\/auth/);
    await page.fill('#username', 'demo');
    await page.fill('#password', 'demo');
    await page.click('#kc-login');

    await page.waitForURL(/\/account/);
    await expect(page.getByRole('heading', { name: 'Mi cuenta' })).toBeVisible();
    await expect(page.getByText('demo@zynema.dev')).toBeVisible();
    await expect(page.getByText('ROLE_user')).toBeVisible();
  });

  test('the session survives a reload and can be closed', async ({ page }) => {
    await page.goto('/account');
    await page.getByRole('button', { name: 'Iniciar sesión' }).click();
    await page.waitForURL(/\/realms\/zynema\/protocol\/openid-connect\/auth/);
    await page.fill('#username', 'demo');
    await page.fill('#password', 'demo');
    await page.click('#kc-login');
    await page.waitForURL(/\/account/);

    await page.reload();
    await expect(page.getByRole('heading', { name: 'Mi cuenta' })).toBeVisible();

    await page.getByRole('button', { name: 'Salir' }).click();
    await page.waitForURL(/http:\/\/localhost:5173\/?$/);
    await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible();
  });
});
