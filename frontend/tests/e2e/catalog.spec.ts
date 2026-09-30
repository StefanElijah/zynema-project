import { expect, test } from '@playwright/test';

const PAGE = {
  content: [
    {
      id: 'a1000000-0000-4000-8000-000000000003',
      slug: 'dune',
      title: 'Dune',
      releaseYear: 2021,
      maturityRating: '13',
      averageRating: 8.2,
      genres: [{ slug: 'ciencia-ficcion', name: 'Ciencia ficción' }],
    },
  ],
  page: 0,
  size: 24,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
};

test('browse shows the grid and keeps the type in the URL', async ({ page }) => {
  await page.route('**/api/v1/web/catalog**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(PAGE) })
  );

  await page.goto('/catalog?type=MOVIE');

  await expect(page.getByRole('heading', { name: 'Películas', level: 1 })).toBeVisible();
  // The card shows the poster; the title is its accessible name.
  await expect(page.getByRole('img', { name: 'Dune' })).toBeVisible();

  // The toggle, not the navbar link with the same label.
  await page.getByRole('main').getByRole('link', { name: 'Series' }).click();
  await expect(page).toHaveURL(/type=SERIES/);
  await expect(page.getByRole('heading', { name: 'Series', level: 1 })).toBeVisible();
});

test('search waits for a query and then shows what the catalogue answered', async ({ page }) => {
  await page.route('**/api/v1/web/catalog/search**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(PAGE) })
  );

  await page.goto('/search');
  await expect(page.getByText('Escribí algo para buscar en el catálogo.')).toBeVisible();

  await page.getByRole('searchbox', { name: 'Buscar en el catálogo' }).fill('dune');
  await page.getByRole('button', { name: 'Buscar' }).click();

  await expect(page).toHaveURL(/q=dune/);
  await expect(page.getByRole('heading', { name: 'Resultados para “dune”' })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Dune' })).toBeVisible();
});
