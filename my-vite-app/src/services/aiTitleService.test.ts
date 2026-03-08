import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { suggestPostTitles } from './aiTitleService';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

describe('aiTitleService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getCsrfTokenMock.mockResolvedValue('csrf');
  });

  it('suggestPostTitles posts csrf header and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { titles: ['t1'], model: 'm' } });

    const res = await suggestPostTitles({ content: 'c', count: 1 });

    expect(res.titles).toEqual(['t1']);
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/ai/posts/title-suggestions');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
      body: JSON.stringify({ content: 'c', count: 1 }),
    });
  });

  it('suggestPostTitles throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(suggestPostTitles({ content: 'c' })).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(suggestPostTitles({ content: 'c' })).rejects.toThrow('生成标题失败');
  });
});
