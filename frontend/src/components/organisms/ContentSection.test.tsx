import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { TitleCard } from '@lib/api';
import { renderWithProviders } from '../../test/renderWithProviders';
import ContentSection from './ContentSection';

const add = vi.fn();

vi.mock('@hooks/useWatchlistActions', () => ({
  useWatchlistActions: () => ({
    canManage: true,
    add,
    remove: vi.fn(),
    isPending: false,
    error: null,
  }),
}));

const DUNE: TitleCard = {
  id: 'a1000000-0000-4000-8000-000000000003',
  type: 'MOVIE',
  title: 'Dune',
  posterUrl: '/dune.jpg',
  releaseYear: 2021,
};

describe('ContentSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing without items', () => {
    renderWithProviders(<ContentSection title="Vacía" items={[]} />);

    expect(screen.queryByRole('heading', { name: 'Vacía' })).not.toBeInTheDocument();
  });

  it('plays a card by navigating to the player', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ContentSection title="Películas populares" items={[DUNE]} />);

    expect(screen.getByRole('heading', { name: 'Películas populares' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reproducir' }));
    expect(screen.getByTestId('location')).toHaveTextContent(
      '/watch/a1000000-0000-4000-8000-000000000003'
    );
  });

  it('adds a card to the list', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ContentSection title="Series" items={[DUNE]} />);

    await user.click(screen.getByRole('button', { name: 'Agregar a mi lista' }));
    expect(add).toHaveBeenCalledWith(DUNE.id);
  });
});
