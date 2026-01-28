import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

vi.mock('../../../../services/postService', () => ({
  getPost: vi.fn(),
  togglePostFavorite: vi.fn(),
  togglePostLike: vi.fn(),
}));

vi.mock('../../../../services/commentService', () => ({
  listPostComments: vi.fn(),
  createPostComment: vi.fn(),
  toggleCommentLike: vi.fn(),
}));

vi.mock('../../../../services/translateService', () => ({
  getTranslateConfig: vi.fn(),
  translatePost: vi.fn(),
  translateComment: vi.fn(),
}));

vi.mock('../../../../services/accountPreferencesService', () => ({
  getMyTranslatePreferences: vi.fn(),
}));

vi.mock('../../../../services/tagService', () => ({
  listTags: vi.fn(),
}));

vi.mock('../../../../services/reportService', () => ({
  reportPost: vi.fn(),
  reportComment: vi.fn(),
}));

import PostDetailPage from './PostDetailPage';
import { getPost } from '../../../../services/postService';
import { listPostComments } from '../../../../services/commentService';
import { getTranslateConfig } from '../../../../services/translateService';
import { getMyTranslatePreferences } from '../../../../services/accountPreferencesService';
import { listTags } from '../../../../services/tagService';

describe('PostDetailPage (comments)', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  const renderPage = async (comments: any[]) => {
    (listTags as any).mockResolvedValue([]);
    (getTranslateConfig as any).mockResolvedValue({ enabled: true, allowedTargetLanguages: [] });
    (getMyTranslatePreferences as any).mockResolvedValue({
      targetLanguage: '简体中文（Simplified Chinese）',
      autoTranslatePosts: false,
      autoTranslateComments: false,
    });
    (getPost as any).mockResolvedValue({
      id: 1,
      boardId: 1,
      title: 't',
      content: 'c',
      contentFormat: 'MARKDOWN',
      metadata: { languages: ['zh'] },
      tags: [],
      hotScore: 0,
      likedByMe: false,
      favoritedByMe: false,
      reactionCount: 0,
      favoriteCount: 0,
      commentCount: comments.length,
    });
    (listPostComments as any).mockResolvedValue({
      content: comments,
      totalPages: 1,
      totalElements: comments.length,
      number: 0,
      size: 20,
    });

    render(
      <MemoryRouter initialEntries={['/posts/1']}>
        <Routes>
          <Route path="/posts/:postId" element={<PostDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText(comments[0].content);
  };

  it('renders compact reply list and places expand button after third child', async () => {
    const comments = [
      { id: 100, postId: 1, parentId: null, content: 'root', authorName: 'Alice', createdAt: '2026-01-01T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 101, postId: 1, parentId: 100, content: 'c1', authorName: 'Bob', createdAt: '2026-01-04T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 102, postId: 1, parentId: 100, content: 'c2', authorName: 'Cat', createdAt: '2026-01-03T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 103, postId: 1, parentId: 100, content: 'c3', authorName: 'Dog', createdAt: '2026-01-02T00:00:00Z', metadata: { languages: ['zh'] } },
      { id: 104, postId: 1, parentId: 100, content: 'c4', authorName: 'Eve', createdAt: '2026-01-01T00:00:00Z', metadata: { languages: ['en'] } },
    ];
    await renderPage(comments);

    const third = await screen.findByText('c3');
    expect(screen.queryByText('c4')).toBeNull();

    const expandBtn = await screen.findByRole('button', { name: '共 4 条回复 · 展开' });
    expect(third.compareDocumentPosition(expandBtn) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    const bobCard = (await screen.findByText('c1')).closest('.rounded-lg') as HTMLElement;
    expect(within(bobCard).getByRole('button', { name: '翻译' })).not.toBeNull();

    const dogCard = third.closest('.rounded-lg') as HTMLElement;
    expect(within(dogCard).queryByRole('button', { name: '翻译' })).toBeNull();

    const rootCard = (await screen.findByText('root')).closest('.rounded-lg') as HTMLElement;
    const rootUser = within(rootCard).getByText('Alice');
    const rootContent = within(rootCard).getByText('root');
    const column = rootContent.closest('.min-w-0.flex-1') as HTMLElement;
    expect(column).not.toBeNull();
    expect(column.contains(rootUser)).toBe(true);
  });

  it('shows thread toggle on every reply when root has >=2 replies', async () => {
    const comments = [
      { id: 100, postId: 1, parentId: null, content: 'root', authorName: 'Alice', createdAt: '2026-01-01T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 101, postId: 1, parentId: 100, content: 'c1', authorName: 'Bob', createdAt: '2026-01-04T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 102, postId: 1, parentId: 100, content: 'c2', authorName: 'Cat', createdAt: '2026-01-03T00:00:00Z', metadata: { languages: ['en'] } },
    ];
    await renderPage(comments);

    const c1Card = (await screen.findByText('c1')).closest('.rounded-lg') as HTMLElement;
    const c2Card = (await screen.findByText('c2')).closest('.rounded-lg') as HTMLElement;
    expect(within(c1Card).getByRole('button', { name: '查看全部对话' })).not.toBeNull();
    expect(within(c2Card).getByRole('button', { name: '查看全部对话' })).not.toBeNull();

    fireEvent.click(within(c1Card).getByRole('button', { name: '查看全部对话' }));
    await screen.findByText('全部对话');
    expect(within(c1Card).getByRole('button', { name: '收起对话' })).not.toBeNull();

    fireEvent.click(within(c1Card).getByRole('button', { name: '收起对话' }));
    expect(screen.queryByText('全部对话')).toBeNull();
  });

  it('hides thread toggle when root has only 1 reply', async () => {
    const comments = [
      { id: 100, postId: 1, parentId: null, content: 'root', authorName: 'Alice', createdAt: '2026-01-01T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 101, postId: 1, parentId: 100, content: 'c1', authorName: 'Bob', createdAt: '2026-01-04T00:00:00Z', metadata: { languages: ['en'] } },
    ];
    await renderPage(comments);

    const c1Card = (await screen.findByText('c1')).closest('.rounded-lg') as HTMLElement;
    expect(within(c1Card).queryByRole('button', { name: '查看全部对话' })).toBeNull();
    expect(within(c1Card).queryByRole('button', { name: '收起对话' })).toBeNull();
  });

  it('shows thread toggle for deep replies after expanding', async () => {
    const comments = [
      { id: 100, postId: 1, parentId: null, content: 'root', authorName: 'Alice', createdAt: '2026-01-01T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 101, postId: 1, parentId: 100, content: 'deep-a', authorName: 'Bob', createdAt: '2026-01-04T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 102, postId: 1, parentId: 101, content: 'deep-b', authorName: 'Cat', createdAt: '2026-01-03T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 103, postId: 1, parentId: 102, content: 'deep-c', authorName: 'Dog', createdAt: '2026-01-02T00:00:00Z', metadata: { languages: ['en'] } },
      { id: 104, postId: 1, parentId: 100, content: 'deep-d', authorName: 'Eve', createdAt: '2026-01-01T00:00:00Z', metadata: { languages: ['en'] } },
    ];
    await renderPage(comments);

    fireEvent.click(await screen.findByRole('button', { name: '共 4 条回复 · 展开' }));

    const aCard = (await screen.findByText('deep-a')).closest('.rounded-lg') as HTMLElement;
    const bCard = (await screen.findByText('deep-b')).closest('.rounded-lg') as HTMLElement;
    const cCard = (await screen.findByText('deep-c')).closest('.rounded-lg') as HTMLElement;
    const dCard = (await screen.findByText('deep-d')).closest('.rounded-lg') as HTMLElement;

    expect(within(aCard).getByRole('button', { name: '查看全部对话' })).not.toBeNull();
    expect(within(bCard).getByRole('button', { name: '查看全部对话' })).not.toBeNull();
    expect(within(cCard).getByRole('button', { name: '查看全部对话' })).not.toBeNull();
    expect(within(dCard).getByRole('button', { name: '查看全部对话' })).not.toBeNull();

    fireEvent.click(within(bCard).getByRole('button', { name: '查看全部对话' }));
    await screen.findByText('全部对话');
    fireEvent.click(await screen.findByRole('button', { name: '关闭' }));
    expect(screen.queryByText('全部对话')).toBeNull();
  });
});
