import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation, useParams } from 'react-router-dom';

vi.mock('../../../../services/portalSearchService', () => ({
  portalSearch: vi.fn(),
}));

import DiscoverSearchPage from './DiscoverSearchPage';
import { portalSearch } from '../../../../services/portalSearchService';

function DetailProbe() {
  const { postId } = useParams();
  const loc = useLocation();
  return (
    <div>
      detail {postId} {loc.search} {loc.hash}
    </div>
  );
}

describe('DiscoverSearchPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders mixed hits and navigates by hit url', async () => {
    (portalSearch as any).mockResolvedValue({
      content: [
        { type: 'POST', postId: 1, title: 'p1', highlightedTitle: '<em>p1</em>', snippet: 's1', highlightedSnippet: '这是<em>命中</em>片段', url: '/portal/posts/detail/1' },
        { type: 'COMMENT', postId: 2, commentId: 20, title: 'p2', snippet: 'c20', url: '/portal/posts/detail/2?commentId=20#comment-20' },
      ],
      totalPages: 1,
      totalElements: 2,
      number: 0,
      size: 20,
      first: true,
      last: true,
      empty: false,
    });

    render(
      <MemoryRouter initialEntries={['/search?q=test']}>
        <Routes>
          <Route path="/search" element={<DiscoverSearchPage />} />
          <Route path="/portal/posts/detail/:postId" element={<DetailProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('p1');
    await screen.findByText('p2');
    expect(screen.getByText('命中').tagName.toLowerCase()).toBe('mark');
    expect(screen.getByText('帖子')).not.toBeNull();
    expect(screen.getByText('评论')).not.toBeNull();

    fireEvent.click(screen.getByText('p2'));
    await screen.findByText(/detail 2/);
  });

  it('falls back to query-term highlight when backend highlight is missing', async () => {
    (portalSearch as any).mockResolvedValue({
      content: [
        { type: 'POST', postId: 3, title: 'Qwen3-VL 对比', snippet: 'Qwen3-VL 与 Qwen3.5-9B 的能力对比', url: '/portal/posts/detail/3' },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
      empty: false,
    });

    render(
      <MemoryRouter initialEntries={['/search?q=Qwen3-VL']}> 
        <Routes>
          <Route path="/search" element={<DiscoverSearchPage />} />
          <Route path="/portal/posts/detail/:postId" element={<DetailProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    expect((await screen.findAllByText('Qwen3-VL')).some((el) => el.tagName.toLowerCase() === 'mark')).toBe(true);
  });
});
