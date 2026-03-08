import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { suggestPostTags } from './aiTagService';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

describe('aiTagService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getCsrfTokenMock.mockResolvedValue('csrf');
  });

  it('suggestPostTags posts csrf header and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { tags: ['a'], model: 'm' } });

    const res = await suggestPostTags({ content: 'c', count: 1 });

    expect(res.tags).toEqual(['a']);
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/ai/posts/tag-suggestions');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
      body: JSON.stringify({ content: 'c', count: 1 }),
    });
  });

  it('suggestPostTags throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(suggestPostTags({ content: 'c' })).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(suggestPostTags({ content: 'c' })).rejects.toThrow('生成标签失败');
  });
});
