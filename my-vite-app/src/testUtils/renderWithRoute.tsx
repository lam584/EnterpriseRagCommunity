import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

export function renderWithRoute(
  element: ReactElement,
  opts?: {
    route?: string;
    path?: string;
  },
) {
  const route = opts?.route ?? '/';
  const path = opts?.path ?? '/';
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path={path} element={element} />
      </Routes>
    </MemoryRouter>,
  );
}

