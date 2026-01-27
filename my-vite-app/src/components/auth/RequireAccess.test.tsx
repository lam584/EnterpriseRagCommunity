import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Outlet, Route, Routes, useOutletContext } from 'react-router-dom';
import { RequireAccess } from './RequireAccess';

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({ isAuthenticated: true, loading: false }),
}));

vi.mock('../../contexts/AccessContext', () => ({
  useAccess: () => ({
    hasPerm: () => true,
    hasRole: () => true,
    loading: false,
    refresh: () => Promise.resolve(),
  }),
}));

function ParentLayout() {
  return <Outlet context={{ foo: 'bar' }} />;
}

function Child() {
  const { foo } = useOutletContext<{ foo: string }>();
  return <div>{foo}</div>;
}

describe('RequireAccess', () => {
  it('forwards parent outlet context to nested routes', () => {
    render(
      <MemoryRouter initialEntries={['/child']}>
        <Routes>
          <Route element={<ParentLayout />}>
            <Route element={<RequireAccess />}>
              <Route path="child" element={<Child />} />
            </Route>
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('bar')).not.toBeNull();
  });
});
