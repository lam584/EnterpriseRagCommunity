import { describe, expect, it, vi, beforeEach } from 'vitest';
import {
  createPost,
  deletePost,
  listPosts,
  listBookmarkedPostsPage,
  listMyPostsPage,
  listMyBookmarkedPostsPage,
  listPostsPage,
  searchAdminPosts,
  searchPosts,
  deletePostFavorite,
  togglePostFavorite,
  togglePostLike,
  updatePost,
} from './postService';
import { mockFetch, mockFetchJsonOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('postService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('searchPosts omits status when status=ALL by default', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await searchPosts({ status: 'ALL', page: 1, pageSize: 20 });
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).not.toContain('status=ALL');
  });

  it('searchPosts keeps status=ALL when preserveAllStatus=true', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await searchPosts({ status: 'ALL', page: 1, pageSize: 20 }, { preserveAllStatus: true });
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).toContain('status=ALL');
  });

  it('searchPosts applies default page/pageSize and omits empty params', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await searchPosts({ keyword: '', boardId: 0 });
    const rawUrl = String((fetchMock as any).mock.calls[0]?.[0]);
    const url = new URL(rawUrl, 'http://localhost');
    expect(url.searchParams.get('page')).toBe('1');
    expect(url.searchParams.get('pageSize')).toBe('25');
    expect(url.searchParams.get('keyword')).toBeNull();
    expect(url.searchParams.get('boardId')).toBe('0');
  });

  it('searchPosts supports omitting query entirely', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await searchPosts();
    const rawUrl = String((fetchMock as any).mock.calls[0]?.[0]);
    const url = new URL(rawUrl, 'http://localhost');
    expect(url.pathname).toBe('/api/posts');
    expect(url.searchParams.get('page')).toBe('1');
    expect(url.searchParams.get('pageSize')).toBe('25');
  });

  it('searchAdminPosts keeps status=ALL by design', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await searchAdminPosts({ status: 'ALL', page: 1, pageSize: 25 });
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/posts?');
    expect(url).toContain('status=ALL');
  });

  it('listPosts returns empty array when backend responds not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    const res = await listPosts();
    expect(res).toEqual([]);
  });

  it('listPosts accepts array response and normalizes tags', async () => {
    mockFetchJsonOnce({
      ok: true,
      json: [{ id: 1, boardId: 1, title: 't', content: 'c', metadata: { tags: ['x'] } }],
    });
    const res = await listPosts();
    expect(res[0]?.id).toBe(1);
    expect(res[0]?.tags).toEqual(['x']);
  });

  it('listPosts returns empty array when response json throws', async () => {
    mockFetchResponseOnce({ ok: true, jsonError: new Error('bad json') });
    await expect(listPosts()).resolves.toEqual([]);
  });

  it('searchPosts returns empty array when backend responds not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(searchPosts({ page: 1, pageSize: 20 })).resolves.toEqual([]);
  });

  it('listPosts maps tags from metadata when tags missing', async () => {
    mockFetchJsonOnce({
      ok: true,
      json: { content: [{ id: 1, boardId: 1, title: 't', content: 'c', metadata: { tags: [' a ', 'b', 3] } }] },
    });
    const res = await listPosts();
    expect(res[0]?.tags).toEqual([' a ', 'b', '3']);
  });

  it('createPost throws fieldErrors when backend returns validation map', async () => {
    mockFetchJsonOnce({ ok: false, json: { title: '必填', content: '太短' } });
    await expect(createPost({ boardId: 1, title: '', content: 'x' })).rejects.toMatchObject({
      fieldErrors: { title: '必填', content: '太短' },
    });
  });

  it('createPost prefers backend message over fallback when not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: '后端说不行', code: 400 } });
    await expect(createPost({ boardId: 1, title: 't', content: 'c' })).rejects.toThrow('后端说不行');
  });

  it('createPost falls back when response json throws', async () => {
    mockFetchResponseOnce({ ok: false, jsonError: new Error('bad json') });
    await expect(createPost({ boardId: 1, title: 't', content: 'c' })).rejects.toThrow('发布失败');
  });

  it('createPost merges tags into metadata and sends csrf header', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 10,
        boardId: 1,
        title: 't',
        content: 'c',
        tags: ['x'],
      }),
    });

    await createPost({ boardId: 1, title: 't', content: 'c', tags: ['x'] });

    const init = (fetchMock as any).mock.calls[0]?.[1];
    expect(init?.headers?.['X-XSRF-TOKEN']).toBe('csrf');
    const body = JSON.parse(String(init?.body));
    expect(body?.metadata?.tags).toEqual(['x']);
    expect(body?.contentFormat).toBe('MARKDOWN');
  });

  it('createPost omits metadata when metadata is not a plain object and tags missing', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 10,
        boardId: 1,
        title: 't',
        content: 'c',
      }),
    });

    await createPost({ boardId: 1, title: 't', content: 'c', metadata: [] as any });

    const init = (fetchMock as any).mock.calls[0]?.[1];
    const body = JSON.parse(String(init?.body));
    expect(body?.metadata).toBeUndefined();
    expect(body?.attachmentIds).toEqual([]);
  });

  it('createPost keeps plain metadata when provided and tags missing', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 10,
        boardId: 1,
        title: 't',
        content: 'c',
      }),
    });

    await createPost({ boardId: 1, title: 't', content: 'c', metadata: { a: 1 } });

    const init = (fetchMock as any).mock.calls[0]?.[1];
    const body = JSON.parse(String(init?.body));
    expect(body?.metadata).toEqual({ a: 1 });
  });

  it('createPost treats non-string validation map as normal failure and uses fallback message', async () => {
    mockFetchJsonOnce({ ok: false, json: { title: '必填', count: 1 } });
    await expect(createPost({ boardId: 1, title: 't', content: 'c' })).rejects.toThrow('发布失败');
  });

  it('createPost returns raw value when backend json is not an object', async () => {
    mockFetchJsonOnce({ ok: true, json: 'ok' });
    await expect(createPost({ boardId: 1, title: 't', content: 'c' })).resolves.toBe('ok' as any);
  });

  it('listPostsPage throws message when response not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: '获取失败' } });
    await expect(listPostsPage({ page: 1, pageSize: 20 })).rejects.toThrow('获取失败');
  });

  it('listPostsPage falls back when backend message is empty', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: '' } });
    await expect(listPostsPage({ page: 1, pageSize: 20 })).rejects.toThrow('获取帖子失败');
  });

  it('listPostsPage accepts array response and normalizes to page shape', async () => {
    mockFetchJsonOnce({
      ok: true,
      json: [{ id: 1, boardId: 1, title: 't', content: 'c' }],
    });
    const page = await listPostsPage({ page: 1, pageSize: 20 });
    expect(page.totalElements).toBe(1);
    expect(page.content?.[0]?.id).toBe(1);
  });

  it('listPostsPage applies defaults and omits status=ALL', async () => {
    const fetchMock = mockFetchJsonOnce({
      ok: true,
      json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 },
    });
    await listPostsPage({ status: 'ALL' });
    const rawUrl = String((fetchMock as any).mock.calls[0]?.[0]);
    const url = new URL(rawUrl, 'http://localhost');
    expect(url.searchParams.get('page')).toBe('1');
    expect(url.searchParams.get('pageSize')).toBe('20');
    expect(url.searchParams.get('status')).toBeNull();
  });

  it('listPosts returns empty array when page content is not an array', async () => {
    mockFetchJsonOnce({ ok: true, json: { content: {} } });
    await expect(listPosts()).resolves.toEqual([]);
  });

  it('listPosts maps tags to empty list when metadata is invalid and tags missing', async () => {
    mockFetchJsonOnce({ ok: true, json: { content: [{ id: 1, boardId: 1, title: 't', content: 'c', metadata: null }] } });
    const res = await listPosts();
    expect(res[0]?.tags).toEqual([]);
  });

  it('listPosts maps tags to empty list when metadata.tags is not an array', async () => {
    mockFetchJsonOnce({ ok: true, json: { content: [{ id: 1, boardId: 1, title: 't', content: 'c', metadata: { tags: 'x' } }] } });
    const res = await listPosts();
    expect(res[0]?.tags).toEqual([]);
  });

  it('apiUrl 默认使用相对路径', async () => {
    const { getPost } = await import('./postService');
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { id: 1, boardId: 1, title: 't', content: 'c' } });
    await expect(getPost(1)).resolves.toMatchObject({ id: 1 });
    expect(String((fetchMock as any).mock.calls[0]?.[0] || '')).toContain('/api/posts/1');
  });

  it('listMyPostsPage accepts array response and normalizes to page shape', async () => {
    mockFetchJsonOnce({
      ok: true,
      json: [{ id: 1, boardId: 1, title: 't', content: 'c' }],
    });
    const page = await listMyPostsPage({ page: 1, pageSize: 20 });
    expect(page.totalElements).toBe(1);
    expect(page.content?.[0]?.id).toBe(1);
  });

  it('listMyPostsPage throws fallback message when backend message is missing', async () => {
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(listMyPostsPage({ page: 1, pageSize: 20 })).rejects.toThrow('获取我的帖子失败');
  });

  it('listMyBookmarkedPostsPage applies defaults for query params', async () => {
    const fetchMock = mockFetchJsonOnce({
      ok: true,
      json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 },
    });
    await listMyBookmarkedPostsPage();
    const rawUrl = String((fetchMock as any).mock.calls[0]?.[0]);
    const url = new URL(rawUrl, 'http://localhost');
    expect(url.pathname).toBe('/api/posts/bookmarks');
    expect(url.searchParams.get('page')).toBe('1');
    expect(url.searchParams.get('pageSize')).toBe('20');
  });

  it('listBookmarkedPostsPage forces favoritedByMe=true', async () => {
    const fetchMock = mockFetchJsonOnce({
      ok: true,
      json: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 },
    });
    await listBookmarkedPostsPage({ page: 2, pageSize: 20 });
    const rawUrl = String((fetchMock as any).mock.calls[0]?.[0]);
    const url = new URL(rawUrl, 'http://localhost');
    expect(url.pathname).toBe('/api/posts');
    expect(url.searchParams.get('favoritedByMe')).toBe('true');
    expect(url.searchParams.get('page')).toBe('2');
  });

  it('updatePost omits metadata when merged metadata is empty', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 1,
        boardId: 1,
        title: 't',
        content: 'c',
      }),
    });

    await updatePost(1, { boardId: 1, title: 't', content: 'c', metadata: null as any });

    const init = (fetchMock as any).mock.calls[0]?.[1];
    const body = JSON.parse(String(init?.body));
    expect(body?.metadata).toBeUndefined();
  });

  it('updatePost throws fieldErrors on validation failure', async () => {
    mockFetchJsonOnce({ ok: false, json: { title: '必填' } });
    await expect(updatePost(1, { boardId: 1, title: '', content: 'c' })).rejects.toMatchObject({
      fieldErrors: { title: '必填' },
    });
  });

  it('updatePost prefers backend field error map over message', async () => {
    mockFetchJsonOnce({ ok: false, json: { title: '必填', message: '后端提示' } });
    await expect(updatePost(1, { boardId: 1, title: '', content: 'c' })).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: expect.objectContaining({ title: '必填' }),
    });
  });

  it('togglePostLike returns null when backend not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'nope' } });
    const res = await togglePostLike(1);
    expect(res).toBeNull();
  });

  it('togglePostFavorite returns payload when backend ok', async () => {
    mockFetchJsonOnce({ ok: true, json: { favoritedByMe: true, favoriteCount: 2 } });
    const res = await togglePostFavorite(1);
    expect(res?.favoritedByMe).toBe(true);
    expect(res?.favoriteCount).toBe(2);
  });

  it('togglePostFavorite returns null when backend not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'nope' } });
    await expect(togglePostFavorite(1)).resolves.toBeNull();
  });

  it('togglePostLike returns null when backend ok but response is not an object', async () => {
    mockFetchJsonOnce({ ok: true, json: 1 });
    await expect(togglePostLike(1)).resolves.toBeNull();
  });

  it('deletePostFavorite returns null when backend not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'nope' } });
    await expect(deletePostFavorite(1)).resolves.toBeNull();
  });

  it('deletePost throws message on failure', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: false,
      json: async () => ({ message: '删不了' }),
    });
    await expect(deletePost(1)).rejects.toThrow('删不了');
  });

  it('deletePost falls back to default message when backend message missing', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: false,
      json: async () => ({}),
    });
    await expect(deletePost(1)).rejects.toThrow('删除失败');
  });
});
