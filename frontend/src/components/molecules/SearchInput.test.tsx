import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import SearchInput from './SearchInput';

describe('SearchInput', () => {
  it('mirrors the committed query when the search page is open', () => {
    render(
      <MemoryRouter initialEntries={['/search?q=arcane']}>
        <SearchInput />
      </MemoryRouter>
    );

    expect(screen.getByRole('searchbox', { name: 'Buscar' })).toHaveValue('arcane');
  });

  it('does not search on an empty submit', async () => {
    const onSearch = vi.fn();
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <SearchInput onSearch={onSearch} />
      </MemoryRouter>
    );
    await user.type(screen.getByRole('searchbox', { name: 'Buscar' }), '   ');
    await user.keyboard('{Enter}');

    expect(onSearch).not.toHaveBeenCalled();
  });

  it('hands over the trimmed query', async () => {
    const onSearch = vi.fn();
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <SearchInput onSearch={onSearch} />
      </MemoryRouter>
    );
    await user.type(screen.getByRole('searchbox', { name: 'Buscar' }), '  dune  ');
    await user.keyboard('{Enter}');

    expect(onSearch).toHaveBeenCalledWith('dune');
  });
});
