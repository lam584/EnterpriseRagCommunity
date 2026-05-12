import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../../../services/content/hotService', () => ({
  fetchHotPosts: vi.fn(),
}));

vi.mock('../components/PostFeed', () => ({
  default: ({ page, loading, error, onRetry }: {
    page: { content?: Array<{ id: number; title?: string | null }> } | null;
    loading: boolean;
    error: string | null;
    onRetry?: () => void;
  }) => (
    <div>
      {loading ? <div>POST_FEED_LOADING</div> : null}
      {error ? <div>POST_FEED_ERROR:{error}</div> : null}
      {(page?.content ?? []).map((post) => (
        <div key={post.id}>{post.title}</div>
      ))}
      {error && onRetry ? (
        <button type="button" onClick={onRetry}>
          POST_FEED_RETRY
        </button>
      ) : null}
    </div>
  ),
}));

import DiscoverHotPage from './DiscoverHotPage';
import { fetchHotPosts } from '../../../../services/content/hotService';

function renderPage() {
  render(
    <MemoryRouter>
      <DiscoverHotPage />
    </MemoryRouter>,
  );
}

describe('DiscoverHotPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads default hot posts, switches window, and pages forward', async () => {
    (fetchHotPosts as any)
      .mockResolvedValueOnce({
        content: [{ post: { id: 1, title: '24h 热帖' }, score: 9.5 }],
        totalPages: 2,
        totalElements: 30,
        number: 0,
        size: 25,
      })
      .mockResolvedValueOnce({
        content: [{ post: { id: 2, title: '7d 热帖' }, score: 8.4 }],
        totalPages: 3,
        totalElements: 70,
        number: 0,
        size: 25,
      })
      .mockResolvedValueOnce({
        content: [{ post: { id: 3, title: '7d 第2页热帖' }, score: 7.7 }],
        totalPages: 3,
        totalElements: 70,
        number: 1,
        size: 25,
      });

    renderPage();

    expect(await screen.findByText('24h 热帖')).not.toBeNull();
    expect(fetchHotPosts).toHaveBeenNthCalledWith(1, { window: '24h', page: 1, pageSize: 25 });
    expect(screen.getByText('第 1 / 2 页')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '7天' }));

    expect(await screen.findByText('7d 热帖')).not.toBeNull();
    expect(fetchHotPosts).toHaveBeenNthCalledWith(2, { window: '7d', page: 1, pageSize: 25 });
    expect(screen.getByText('第 1 / 3 页')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    expect(await screen.findByText('7d 第2页热帖')).not.toBeNull();
    expect(fetchHotPosts).toHaveBeenNthCalledWith(3, { window: '7d', page: 2, pageSize: 25 });
    expect(screen.getByText('第 2 / 3 页')).not.toBeNull();
  });

  it('shows error state and retries the current request', async () => {
    (fetchHotPosts as any)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce({
        content: [{ post: { id: 4, title: '恢复后的热帖' }, score: 6.6 }],
        totalPages: 1,
        totalElements: 1,
        number: 0,
        size: 25,
      });

    renderPage();

    expect(await screen.findByText('POST_FEED_ERROR:boom')).not.toBeNull();
    expect(fetchHotPosts).toHaveBeenNthCalledWith(1, { window: '24h', page: 1, pageSize: 25 });

    fireEvent.click(screen.getByRole('button', { name: 'POST_FEED_RETRY' }));

    expect(await screen.findByText('恢复后的热帖')).not.toBeNull();
    expect(fetchHotPosts).toHaveBeenNthCalledWith(2, { window: '24h', page: 1, pageSize: 25 });
  });
});