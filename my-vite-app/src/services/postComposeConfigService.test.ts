import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getPostComposeConfig } from './postComposeConfigService';

describe('postComposeConfigService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('getPostComposeConfig sends GET and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { requireTitle: true, maxContentChars: 200 } });

    const res = await getPostComposeConfig();

    expect(res.requireTitle).toBe(true);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/public/posts/compose-config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getPostComposeConfig throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getPostComposeConfig()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getPostComposeConfig()).rejects.toThrow('获取发帖配置失败');
  });

  it('getPostComposeConfig covers json parse fallback', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getPostComposeConfig()).resolves.toEqual({});

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(getPostComposeConfig()).rejects.toThrow('获取发帖配置失败');
  });
});
