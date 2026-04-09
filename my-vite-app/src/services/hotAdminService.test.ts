import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { adminRecomputeHotScores } from './hotAdminService';

describe('hotAdminService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('adminRecomputeHotScores maps 1y to recompute-1y endpoint', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { ok: true, window: 'Y1' } });

    const res = await adminRecomputeHotScores('1y');

    expect(res.window).toBe('Y1');
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/api/admin/hot-scores/recompute-1y');
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('include');
  });

  it('adminRecomputeHotScores for 1y throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminRecomputeHotScores('1y')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(adminRecomputeHotScores('1y')).rejects.toThrow('重算热度分失败');
  });
});
