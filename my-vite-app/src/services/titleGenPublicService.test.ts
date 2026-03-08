import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getPostTitleGenPublicConfig } from './titleGenPublicService';

describe('titleGenPublicService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('getPostTitleGenPublicConfig sends GET and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, defaultCount: 1, maxCount: 3 } });

    const res = await getPostTitleGenPublicConfig();

    expect(res.defaultCount).toBe(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/ai/posts/title-gen/config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getPostTitleGenPublicConfig throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getPostTitleGenPublicConfig()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getPostTitleGenPublicConfig()).rejects.toThrow('获取标题生成配置失败');
  });

  it('getPostTitleGenPublicConfig covers json parse fallback', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getPostTitleGenPublicConfig()).resolves.toEqual({});

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(getPostTitleGenPublicConfig()).rejects.toThrow('获取标题生成配置失败');
  });
});
