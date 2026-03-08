import { describe, expect, it, vi, beforeEach } from 'vitest';
import { portalSearch } from './portalSearchService';
import { mockFetchJsonOnce } from '../testUtils/mockFetch';

describe('portalSearchService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('portalSearch returns page shape on success', async () => {
    mockFetchJsonOnce({
      ok: true,
      json: { content: [{ type: 'POST', postId: 1 }], totalPages: 1, totalElements: 1, number: 0, size: 20, first: true, last: true, empty: false },
    });
    const res = await portalSearch('q', 1, 20, null);
    expect(res.content?.[0]?.postId).toBe(1);
  });

  it('portalSearch throws backend message on failure', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: '搜索挂了' } });
    await expect(portalSearch('q')).rejects.toThrow('搜索挂了');
  });

  it('portalSearch filters empty values in query string', async () => {
    const fetchMock = mockFetchJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 } });
    await portalSearch('q', 1, 20, 'ALL' as unknown as number);
    const url = fetchMock.mock.calls[0]?.[0] as string;
    expect(url).toContain('q=q');
    expect(url).toContain('page=1');
    expect(url).toContain('pageSize=20');
    expect(url).not.toContain('boardId=ALL');
  });

  it('portalSearch throws fallback when backend message missing', async () => {
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(portalSearch('q')).rejects.toThrow('搜索失败');
  });
});

