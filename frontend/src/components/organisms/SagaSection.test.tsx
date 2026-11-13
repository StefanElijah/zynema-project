import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import SagaSection from './SagaSection';

const ITEMS = [
  {
    id: 'f1',
    slug: 'the-dark-knight-trilogy',
    name: 'The Dark Knight Trilogy',
    description: 'The complete trilogy.',
    coverUrl: '/assets/movies/images/trilogy.jpg',
    itemCount: 3,
  },
];

describe('SagaSection', () => {
  it('renders a large linked card for each saga', () => {
    renderWithProviders(<SagaSection items={ITEMS} />);

    expect(screen.getByRole('heading', { name: 'Sagas y franquicias' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /The Dark Knight Trilogy/ })).toHaveAttribute(
      'href',
      '/saga/the-dark-knight-trilogy'
    );
    expect(screen.getByText('3 títulos')).toBeInTheDocument();
  });

  it('renders nothing when there are no sagas', () => {
    renderWithProviders(<SagaSection items={[]} />);

    expect(screen.queryByRole('heading', { name: 'Sagas y franquicias' })).not.toBeInTheDocument();
  });
});
