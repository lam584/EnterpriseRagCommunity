import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getPostTagGenPublicConfig } from './tagGenPublicService';

describe('tagGenPublicService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('getPostTagGenPublicConfig sends GET and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, defaultCount: 3, maxCount: 5, maxContentChars: 2000 } });

    const res = await getPostTagGenPublicConfig();

    expect(res.maxCount).toBe(5);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/ai/posts/tag-gen/config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getPostTagGenPublicConfig throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getPostTagGenPublicConfig()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getPostTagGenPublicConfig()).rejects.toThrow('获取主题标签生成配置失败');
  });

  it('getPostTagGenPublicConfig covers json parse fallback', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getPostTagGenPublicConfig()).resolves.toEqual({});

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(getPostTagGenPublicConfig()).rejects.toThrow('获取主题标签生成配置失败');
  });
});
