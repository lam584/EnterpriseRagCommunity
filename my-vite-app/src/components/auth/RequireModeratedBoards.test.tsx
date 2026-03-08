import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';

const { listMyModeratedBoardsMock } = vi.hoisted(() => ({
  listMyModeratedBoardsMock: vi.fn(),
}));

vi.mock('../../services/moderatorBoardsService', () => ({
  listMyModeratedBoards: listMyModeratedBoardsMock,
}));

import RequireModeratedBoards from './RequireModeratedBoards';

describe('RequireModeratedBoards', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders outlet when moderated boards exist', async () => {
    listMyModeratedBoardsMock.mockResolvedValueOnce([{ id: 1 }]);

    render(
      <MemoryRouter initialEntries={['/m']}>
        <Routes>
          <Route path="/m" element={<RequireModeratedBoards />}>
            <Route index element={<div>OUTLET_OK</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText('OUTLET_OK')).toBeTruthy();
    });
  });

  it('renders empty-state message when moderated boards list is empty', async () => {
    listMyModeratedBoardsMock.mockResolvedValueOnce([]);

    render(
      <MemoryRouter initialEntries={['/m']}>
        <Routes>
          <Route path="/m" element={<RequireModeratedBoards />}>
            <Route index element={<div>OUTLET_OK</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('加载版主权限中…')).toBeTruthy();
    await waitFor(() => {
      expect(screen.getByText('你目前没有被设置为任何版块的版主。')).toBeTruthy();
    });
    expect(screen.queryByText('OUTLET_OK')).toBeNull();
  });

  it('renders error when listMyModeratedBoards rejects', async () => {
    listMyModeratedBoardsMock.mockRejectedValueOnce(new Error('boom'));

    render(
      <MemoryRouter initialEntries={['/m']}>
        <Routes>
          <Route path="/m" element={<RequireModeratedBoards />}>
            <Route index element={<Outlet />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText('boom')).toBeTruthy();
    });
  });

  it('converts non-Error rejection to string', async () => {
    listMyModeratedBoardsMock.mockRejectedValueOnce('bad-state');

    render(
      <MemoryRouter initialEntries={['/m']}>
        <Routes>
          <Route path="/m" element={<RequireModeratedBoards />}>
            <Route index element={<Outlet />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText('bad-state')).toBeTruthy();
    });
  });
});
