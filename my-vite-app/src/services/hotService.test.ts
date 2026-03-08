import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { fetchHotPosts } from './hotService';

describe('hotService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('fetchHotPosts uses defaults and returns page', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [], totalPages: 1, totalElements: 0, number: 0, size: 10 } });

    const res = await fetchHotPosts();

    expect(res.totalPages).toBe(1);
    const url = fetchMock.mock.calls[0]?.[0] as string;
    expect(url).toContain('/api/hot?');
    expect(url).toContain('window=24h');
    expect(url).toContain('page=1');
    expect(url).toContain('pageSize=10');
  });

  it('fetchHotPosts throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(fetchHotPosts()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(fetchHotPosts()).rejects.toThrow('获取热榜失败');
  });
});
