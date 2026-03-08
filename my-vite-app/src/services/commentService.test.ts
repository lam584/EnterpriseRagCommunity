import { describe, expect, it, vi, beforeEach } from 'vitest';
import { adminListComments, adminSetCommentDeleted, adminUpdateCommentStatus, createPostComment, listPostComments, toggleCommentLike } from './commentService';
import { mockFetch, mockFetchJsonOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('commentService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('listPostComments builds includeMinePending flag', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await listPostComments(9, 1, 20, true);
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).toContain('/api/posts/9/comments?');
    expect(url).toContain('includeMinePending=true');
  });

  it('listPostComments builds includeMinePending=false flag', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await listPostComments(9, 1, 20, false);
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).toContain('includeMinePending=false');
  });

  it('listPostComments throws backend message and default message', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: '获取评论失败原因' } });
    await expect(listPostComments(9)).rejects.toThrow('获取评论失败原因');

    mockFetchJsonOnce({ ok: false, json: { message: 1 } });
    await expect(listPostComments(9)).rejects.toThrow('获取评论失败');

    mockFetchJsonOnce({ ok: false, json: null });
    await expect(listPostComments(9)).rejects.toThrow('获取评论失败');
  });

  it('listPostComments uses json catch fallback', async () => {
    mockFetchResponseOnce({ ok: false, jsonError: new Error('bad') });
    await expect(listPostComments(9)).rejects.toThrow('获取评论失败');
  });

  it('createPostComment sends csrf header and body', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: true,
      json: async () => ({ id: 1, postId: 9, content: 'c' }),
    });
    const res = await createPostComment(9, { content: 'c' });
    expect(res.id).toBe(1);
    const init = (fetchMock as any).mock.calls[0]?.[1];
    expect(init.headers?.['X-XSRF-TOKEN']).toBe('csrf');
    expect(JSON.parse(String(init.body))).toEqual({ content: 'c' });
  });

  it('createPostComment throws backend message and default message', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: '发表评论失败原因' } });
    await expect(createPostComment(9, { content: 'c' })).rejects.toThrow('发表评论失败原因');

    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(createPostComment(9, { content: 'c' })).rejects.toThrow('发表评论失败');

    mockFetchResponseOnce({ ok: false, jsonError: new Error('bad') });
    await expect(createPostComment(9, { content: 'c' })).rejects.toThrow('发表评论失败');
  });

  it('toggleCommentLike throws backend message when not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: '点赞失败原因' } });
    await expect(toggleCommentLike(1)).rejects.toThrow('点赞失败原因');
  });

  it('toggleCommentLike returns json on ok', async () => {
    await mockFetchJsonOnce({ ok: true, json: { likedByMe: true, likeCount: 2 } });
    await expect(toggleCommentLike(1)).resolves.toEqual({ likedByMe: true, likeCount: 2 });
  });

  it('adminListComments omits empty fields and uses page defaults', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await adminListComments({ page: 2, authorName: '' });
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/comments?');
    expect(url).toContain('page=2');
    expect(url).toContain('pageSize=20');
    expect(url).not.toContain('authorName=');
  });

  it('adminListComments keeps boolean false and supports time range', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await adminListComments({ isDeleted: false, createdFrom: '2026-01-01T00:00:00Z', createdTo: '2026-01-02T00:00:00Z' });
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).toContain('isDeleted=false');
    expect(url).toContain('createdFrom=2026-01-01T00%3A00%3A00Z');
    expect(url).toContain('createdTo=2026-01-02T00%3A00%3A00Z');
  });

  it('adminListComments omits status when empty string', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await adminListComments({ status: '' as any });
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).not.toContain('status=');
  });

  it('adminListComments includes status when provided', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [] } });
    await adminListComments({ status: 'VISIBLE' });
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).toContain('status=VISIBLE');
  });

  it('adminUpdateCommentStatus and adminSetCommentDeleted cover ok/error branches', async () => {
    mockFetchJsonOnce({ ok: true, json: { id: 1, postId: 2, authorId: 3, content: 'c', status: 'VISIBLE', isDeleted: false, createdAt: 't', updatedAt: 't2' } });
    await expect(adminUpdateCommentStatus(1, 'HIDDEN')).resolves.toMatchObject({ id: 1, status: 'VISIBLE' });

    mockFetchJsonOnce({ ok: false, json: { message: '更新失败原因' } });
    await expect(adminUpdateCommentStatus(1, 'HIDDEN')).rejects.toThrow('更新失败原因');

    mockFetchResponseOnce({ ok: false, jsonError: new Error('bad') });
    await expect(adminSetCommentDeleted(1, true)).rejects.toThrow('更新删除状态失败');
  });

  it('adminSetCommentDeleted sends csrf header and body on success', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: true,
      json: async () => ({ id: 1, postId: 2, authorId: 3, content: 'c', status: 'VISIBLE', isDeleted: true, createdAt: 't', updatedAt: 't2' }),
    });
    await expect(adminSetCommentDeleted(1, true)).resolves.toMatchObject({ id: 1, isDeleted: true });
    const init = (fetchMock as any).mock.calls[0]?.[1];
    expect(init.headers?.['X-XSRF-TOKEN']).toBe('csrf');
    expect(JSON.parse(String(init.body))).toEqual({ isDeleted: true });
  });
});
