import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getUploadFormatsConfig } from './uploadFormatsPublicService';

describe('uploadFormatsPublicService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('getUploadFormatsConfig sends GET and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, maxFilesPerRequest: 2, formats: [{ format: 'pdf', enabled: true }] } });

    const res = await getUploadFormatsConfig();

    expect(res.enabled).toBe(true);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/public/uploads/formats-config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getUploadFormatsConfig throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getUploadFormatsConfig()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getUploadFormatsConfig()).rejects.toThrow('获取上传格式配置失败');
  });

  it('getUploadFormatsConfig covers json parse fallback', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getUploadFormatsConfig()).resolves.toEqual({});

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(getUploadFormatsConfig()).rejects.toThrow('获取上传格式配置失败');
  });
});
