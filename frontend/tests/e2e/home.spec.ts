import { expect, test } from '@playwright/test';

/**
 * The landing page against a stubbed BFF. The stack is not needed: the page's
 * contract is the shape of `GET /web/home`, and that shape is what the fixture
 * freezes — a rename in the BFF breaks this test even without a backend.
 */
const HOME = {
  hero: {
    card: {
      id: 'a2000000-0000-4000-8000-000000000002',
      slug: 'arcane',
      title: 'Arcane',
      backdropUrl: null,
    },
    synopsis: 'En las ciudades de Piltover y Zaun.',
    tagline: 'Serie original',
  },
  rows: [
    {
      id: 'popular-series',
      title: 'Series populares',
      items: [
        {
          id: 'a2000000-0000-4000-8000-000000000002',
          slug: 'arcane',
          title: 'Arcane',
          releaseYear: 2021,
          maturityRating: '16',
          averageRating: 9,
          genres: [{ slug: 'drama', name: 'Drama' }],
        },
      ],
    },
  ],
};

test('home renders the hero and the rails the BFF composed', async ({ page }) => {
  await page.route('**/api/v1/web/home', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(HOME) })
  );

  await page.goto('/');

  await expect(page).toHaveTitle(/Zynema/);
  await expect(page.getByRole('heading', { name: 'Arcane', level: 1 })).toBeVisible();
  await expect(page.getByText('En las ciudades de Piltover y Zaun.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Series populares' })).toBeVisible();
});

test('home says so when the catalogue is down, and retries', async ({ page }) => {
  // Down until the user acts: React Query's own retry must not mask the error
  // state this test exists to check.
  let down = true;
  await page.route('**/api/v1/web/home', (route) =>
    down
      ? route.fulfill({ status: 503, contentType: 'application/json', body: '{}' })
      : route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(HOME) })
  );

  await page.goto('/');

  await expect(page.getByText('No pudimos cargar el catálogo.')).toBeVisible();

  down = false;
  await page.getByRole('button', { name: 'Reintentar' }).click();
  await expect(page.getByRole('heading', { name: 'Arcane', level: 1 })).toBeVisible();
});
