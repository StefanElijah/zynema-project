import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import HeroSection from './HeroSection';

describe('HeroSection', () => {
  it('renders nothing without a title', () => {
    const { container } = render(<HeroSection synopsis="sin título" />);

    expect(container).toBeEmptyDOMElement();
  });

  it('shows the copy and the two actions', async () => {
    const onPlay = vi.fn();
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <HeroSection
          title="Arcane"
          synopsis="En las ciudades de Piltover y Zaun."
          tagline="Serie original"
          detailPath="/title/arcane"
          onPlay={onPlay}
        />
      </MemoryRouter>
    );

    expect(screen.getByRole('heading', { name: 'Arcane', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('En las ciudades de Piltover y Zaun.')).toBeInTheDocument();
    expect(screen.getByText('Serie original')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Más info' })).toHaveAttribute('href', '/title/arcane');

    await user.click(screen.getByRole('button', { name: /Reproducir/ }));
    expect(onPlay).toHaveBeenCalled();
  });

  it('paints the backdrop behind the copy', () => {
    const { container } = render(
      <MemoryRouter>
        <HeroSection title="Dune" backdropUrl="/dune-backdrop.jpg" />
      </MemoryRouter>
    );

    const backdrop = container.querySelector('[style*="dune-backdrop.jpg"]');
    expect(backdrop).not.toBeNull();
  });
});
