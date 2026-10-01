import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders the poster with the title as its accessible name', () => {
    render(<ContentCard item={CARD} />);

    expect(screen.getByRole('img', { name: 'Dune' })).toBeInTheDocument();
  });

  it('falls back to the placeholder when the poster cannot load', () => {
    render(<ContentCard item={CARD} />);

    fireEvent.error(screen.getByRole('img', { name: 'Dune' }));

    expect(screen.getByRole('img', { name: 'Dune' })).toHaveAttribute(
      'src',
      expect.stringContaining('data:image/svg')
    );
  });

  it('plays and adds to the list from the real buttons', () => {
    const onPlay = vi.fn();
    const onAdd = vi.fn();

    render(<ContentCard item={CARD} onPlay={onPlay} onAdd={onAdd} />);
    fireEvent.click(screen.getByRole('button', { name: 'Reproducir' }));
    fireEvent.click(screen.getByRole('button', { name: 'Agregar a mi lista' }));

    expect(onPlay).toHaveBeenCalledWith(CARD);
    expect(onAdd).toHaveBeenCalledWith(CARD);
  });

  it('opens the details modal from the info button', () => {
    render(<ContentCard item={CARD} />);
    fireEvent.click(screen.getByRole('button', { name: 'Más información' }));

    expect(screen.getByRole('heading', { name: 'Dune' })).toBeInTheDocument();
    // The genre appears twice: on the card overlay and inside the modal.
    expect(screen.getAllByText('Ciencia ficción').length).toBeGreaterThan(0);
  });

  it('opens from the poster button and closes from the backdrop', () => {
    render(<ContentCard item={CARD} />);
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalles de Dune' }));
    expect(screen.getByRole('heading', { name: 'Dune' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar detalles' }));
    expect(screen.queryByRole('heading', { name: 'Dune' })).not.toBeInTheDocument();
  });

  it('closes the modal with its own close button', () => {
    render(<ContentCard item={CARD} />);
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalles de Dune' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar' }));

    expect(screen.queryByRole('heading', { name: 'Dune' })).not.toBeInTheDocument();
  });

  it('plays from the modal and closes it', () => {
    const onPlay = vi.fn();

    render(<ContentCard item={CARD} onPlay={onPlay} />);
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalles de Dune' }));

    // The card overlay and the modal both offer "Reproducir"; the modal is the
    // last one in the DOM (it is portaled after the card).
    const playButtons = screen.getAllByRole('button', { name: 'Reproducir' });
    fireEvent.click(playButtons[playButtons.length - 1]);

    expect(onPlay).toHaveBeenCalledWith(CARD);
    expect(screen.queryByRole('heading', { name: 'Dune' })).not.toBeInTheDocument();
  });

  it('fires the modal quick actions', () => {
    const onAdd = vi.fn();
    const onLike = vi.fn();
    const onDislike = vi.fn();
    const onFavorite = vi.fn();

    render(
      <ContentCard
        item={CARD}
        onAdd={onAdd}
        onLike={onLike}
        onDislike={onDislike}
        onFavorite={onFavorite}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalles de Dune' }));

    fireEvent.click(screen.getByRole('button', { name: 'Mi Lista' }));
    fireEvent.click(screen.getByRole('button', { name: 'Me gusta' }));
    fireEvent.click(screen.getByRole('button', { name: 'No me gusta' }));
    fireEvent.click(screen.getByRole('button', { name: 'Me encanta' }));

    expect(onAdd).toHaveBeenCalledWith(CARD);
    expect(onLike).toHaveBeenCalledWith(CARD);
    expect(onDislike).toHaveBeenCalledWith(CARD);
    expect(onFavorite).toHaveBeenCalledWith(CARD);
  });

  it('expands on hover into the portal and collapses on leave', () => {
    render(<ContentCard item={CARD} />);

    act(() => {
      fireEvent.mouseEnter(
        screen.getByRole('img', { name: 'Dune' }).closest('div')!.parentElement!
      );
      vi.advanceTimersByTime(300);
    });

    expect(screen.getByRole('heading', { name: 'Dune', level: 5 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mi lista' })).toBeInTheDocument();

    act(() => {
      fireEvent.mouseLeave(screen.getByRole('heading', { name: 'Dune', level: 5 }));
      vi.advanceTimersByTime(150);
    });

    expect(screen.queryByRole('heading', { name: 'Dune', level: 5 })).not.toBeInTheDocument();
  });

  it('fires the quick actions of the expanded card', () => {
    const onAdd = vi.fn();
    const onLike = vi.fn();
    const onDislike = vi.fn();
    const onFavorite = vi.fn();
    const onDismiss = vi.fn();

    render(
      <ContentCard
        item={CARD}
        onAdd={onAdd}
        onLike={onLike}
        onDislike={onDislike}
        onFavorite={onFavorite}
        onDismiss={onDismiss}
      />
    );

    act(() => {
      fireEvent.mouseEnter(
        screen.getByRole('img', { name: 'Dune' }).closest('div')!.parentElement!
      );
      vi.advanceTimersByTime(300);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Mi lista' }));
    fireEvent.click(screen.getByTitle('Me gusta'));
    fireEvent.click(screen.getByTitle('No me gusta'));
    fireEvent.click(screen.getByTitle('Me encanta'));
    fireEvent.click(screen.getByTitle('No me interesa'));

    expect(onAdd).toHaveBeenCalledWith(CARD);
    expect(onLike).toHaveBeenCalledWith(CARD);
    expect(onDislike).toHaveBeenCalledWith(CARD);
    expect(onFavorite).toHaveBeenCalledWith(CARD);
    expect(onDismiss).toHaveBeenCalledWith(CARD);
  });

  it('keeps a single hover timer when the pointer re-enters', () => {
    render(<ContentCard item={CARD} />);
    const wrapper = screen.getByRole('img', { name: 'Dune' }).closest('div')!.parentElement!;

    act(() => {
      fireEvent.mouseEnter(wrapper);
      fireEvent.mouseEnter(wrapper);
      vi.advanceTimersByTime(300);
    });

    expect(screen.getByRole('heading', { name: 'Dune', level: 5 })).toBeInTheDocument();
  });

  it('positions the expanded card against every viewport edge', () => {
    const rects = [
      { left: 400, right: 600, width: 200, top: 100 }, // centred
      { left: 900, right: 1100, width: 200, top: 100 }, // against the right edge
      { left: 10, right: 210, width: 200, top: 100 }, // against the left edge
      { left: 300, right: 1024, width: 724, top: 100 }, // no room either side
    ];

    for (const rect of rects) {
      const { unmount } = render(<ContentCard item={CARD} />);
      vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
        ...rect,
        bottom: rect.top + 300,
        height: 300,
        x: rect.left,
        y: rect.top,
        toJSON: () => rect,
      });

      act(() => {
        fireEvent.mouseEnter(
          screen.getByRole('img', { name: 'Dune' }).closest('div')!.parentElement!
        );
        vi.advanceTimersByTime(300);
      });

      expect(screen.getByRole('heading', { name: 'Dune', level: 5 })).toBeInTheDocument();
      unmount();
      vi.restoreAllMocks();
    }
  });

  it('opens the modal from the expanded card', () => {
    render(<ContentCard item={CARD} />);

    act(() => {
      fireEvent.mouseEnter(
        screen.getByRole('img', { name: 'Dune' }).closest('div')!.parentElement!
      );
      vi.advanceTimersByTime(300);
    });

    const infoButtons = screen.getAllByRole('button', { name: 'Más información' });
    fireEvent.click(infoButtons[infoButtons.length - 1]);

    expect(screen.getByRole('heading', { name: 'Dune', level: 2 })).toBeInTheDocument();
  });
});
