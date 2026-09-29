import { expect, test } from '@playwright/test';

const DETAIL = {
  content: {
    id: 'a2000000-0000-4000-8000-000000000002',
    slug: 'arcane',
    type: 'SERIES',
    title: 'Arcane',
    synopsis: 'En las ciudades de Piltover y Zaun.',
    releaseYear: 2021,
    maturityRating: '16',
    averageRating: 9,
    genres: [{ slug: 'drama', name: 'Drama' }],
    seasons: [{ seasonNumber: 1, episodeCount: 9 }],
    credits: [{ personName: 'Hailee Steinfeld', role: 'ACTOR' }],
  },
  playback: { allowed: false, reason: 'AUTHENTICATION_REQUIRED' },
  degraded: [],
};

test('detail turns the BFF context into the right call to action', async ({ page }) => {
  await page.route('**/api/v1/web/catalog/arcane**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(DETAIL) })
  );

  await page.goto('/title/arcane');

  await expect(page.getByRole('heading', { name: 'Arcane', level: 1 })).toBeVisible();
  await expect(page.getByText('Temporada 1')).toBeVisible();
  await expect(page.getByText('Hailee Steinfeld')).toBeVisible();

  // Anonymous: the answer says authentication, so that is the button.
  await expect(page.getByRole('button', { name: 'Iniciar sesión para ver' })).toBeVisible();
});
