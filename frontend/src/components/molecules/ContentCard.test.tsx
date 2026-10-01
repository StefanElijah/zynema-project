import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { TitleCard } from '@lib/api';
import ContentCard from './ContentCard';

const CARD: TitleCard = {
  id: 'a1000000-0000-4000-8000-000000000003',
  type: 'MOVIE',
  title: 'Dune',
  posterUrl: '/dune.jpg',
  releaseYear: 2021,
  maturityRating: '13',
  averageRating: 8.2,
  genres: [{ slug: 'ciencia-ficcion', name: 'Ciencia ficción' }],
};

describe('ContentCard', () => {
  it('renders the poster with the title as its accessible name', () => {
    render(<ContentCard item={CARD} />);

    expect(screen.getByRole('img', { name: 'Dune' })).toBeInTheDocument();
  });

  it('plays and adds to the list from the real buttons', async () => {
    const onPlay = vi.fn();
    const onAdd = vi.fn();
    const user = userEvent.setup();

    render(<ContentCard item={CARD} onPlay={onPlay} onAdd={onAdd} />);
    await user.click(screen.getByRole('button', { name: 'Reproducir' }));
    await user.click(screen.getByRole('button', { name: 'Agregar a mi lista' }));

    expect(onPlay).toHaveBeenCalledWith(CARD);
    expect(onAdd).toHaveBeenCalledWith(CARD);
  });

  it('opens the details modal from the info button', async () => {
    const user = userEvent.setup();

    render(<ContentCard item={CARD} />);
    await user.click(screen.getByRole('button', { name: 'Más información' }));

    expect(screen.getByRole('heading', { name: 'Dune' })).toBeInTheDocument();
    // The genre appears twice: on the card overlay and inside the modal.
    expect(screen.getAllByText('Ciencia ficción').length).toBeGreaterThan(0);
  });

  it('opens from the poster button and closes from the backdrop', async () => {
    const user = userEvent.setup();

    render(<ContentCard item={CARD} />);
    await user.click(screen.getByRole('button', { name: 'Ver detalles de Dune' }));
    expect(screen.getByRole('heading', { name: 'Dune' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cerrar detalles' }));
    expect(screen.queryByRole('heading', { name: 'Dune' })).not.toBeInTheDocument();
  });
});
