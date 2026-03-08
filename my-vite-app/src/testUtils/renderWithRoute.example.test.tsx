import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import { useLocation, useParams } from 'react-router-dom';
import { renderWithRoute } from './renderWithRoute';

function ExamplePage() {
  const { userId } = useParams<{ userId: string }>();
  const loc = useLocation();
  return (
    <div>
      <div data-testid="userId">{String(userId ?? '')}</div>
      <div data-testid="pathname">{loc.pathname}</div>
      <div data-testid="search">{loc.search}</div>
    </div>
  );
}

describe('renderWithRoute (example)', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders a page component at a specific route and path', () => {
    renderWithRoute(<ExamplePage />, {
      route: '/users/42?tab=profile',
      path: '/users/:userId',
    });

    expect(screen.getByTestId('userId').textContent).toBe('42');
    expect(screen.getByTestId('pathname').textContent).toBe('/users/42');
    expect(screen.getByTestId('search').textContent).toBe('?tab=profile');
  });

  it('uses default route and path when options are omitted', () => {
    renderWithRoute(<ExamplePage />);
    expect(screen.getByTestId('pathname').textContent).toBe('/');
    expect(screen.getByTestId('search').textContent).toBe('');
  });
});
