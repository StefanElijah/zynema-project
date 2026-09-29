import { test, expect } from '@playwright/test';

test('home page loads and shows the Zynema brand', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Zynema/);
  await expect(page.getByText('Zynema').first()).toBeVisible();
});
