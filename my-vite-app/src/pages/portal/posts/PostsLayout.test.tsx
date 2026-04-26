import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import PostsLayout from './PostsLayout';

vi.mock('./components/PostsCreatePreviewSidebar', () => ({
  default: () => <div data-testid="compose-preview-sidebar" />,
}));

function renderPostsLayout(initialEntry: string | { pathname: string; state?: unknown }) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route
          path="/"
          element={<Outlet context={{ composePreviewOpen: false, setComposePreviewOpen: vi.fn() }} />}
        >
          <Route
            path="/portal/posts/*"
            element={<PostsLayout />}
          >
            <Route path="*" element={<div data-testid="posts-layout-outlet" />} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('PostsLayout', () => {
  it('keeps discover header when post detail comes from discover', () => {
    renderPostsLayout({
      pathname: '/portal/posts/detail/123',
      state: { from: { pathname: '/portal/discover/home' } },
    });

    expect(screen.getByRole('heading', { name: '浏览与发现' })).not.toBeNull();
    expect(screen.getByRole('link', { name: '首页' })).not.toBeNull();
    expect(screen.queryByRole('heading', { name: '发帖' })).toBeNull();
  });

  it('keeps compose header for create route', () => {
    renderPostsLayout('/portal/posts/create');

    expect(screen.getByRole('heading', { name: '发帖' })).not.toBeNull();
    expect(screen.getByRole('link', { name: '草稿箱' })).not.toBeNull();
  });
});