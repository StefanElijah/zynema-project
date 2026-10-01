import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Callback from './Callback';

describe('Callback', () => {
  it('tells the visitor the sign-in is completing', () => {
    render(<Callback />);

    expect(screen.getByText('Completando inicio de sesión…')).toBeInTheDocument();
  });
});
