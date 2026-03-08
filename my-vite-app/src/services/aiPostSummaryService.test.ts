import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getPostAiSummary, getPostSummaryConfig } from './aiPostSummaryService';

describe('aiPostSummaryService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('getPostSummaryConfig sends GET and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true } });

    const res = await getPostSummaryConfig();

    expect(res.enabled).toBe(true);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/ai/posts/summary/config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getPostSummaryConfig throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getPostSummaryConfig()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getPostSummaryConfig()).rejects.toThrow('获取摘要配置失败');
  });

  it('getPostAiSummary sends GET and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { postId: 9, enabled: true, status: 'DONE' } });

    const res = await getPostAiSummary(9);

    expect(res.postId).toBe(9);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/ai/posts/9/summary');
  });

  it('getPostAiSummary throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'oops' } });
    await expect(getPostAiSummary(1)).rejects.toThrow('oops');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getPostAiSummary(1)).rejects.toThrow('获取AI摘要失败');
  });

  it('covers json parse fallback for config and summary', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getPostSummaryConfig()).resolves.toEqual({});

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(getPostSummaryConfig()).rejects.toThrow('获取摘要配置失败');

    mockFetchResponseOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getPostAiSummary(9)).resolves.toEqual({});

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(getPostAiSummary(9)).rejects.toThrow('获取AI摘要失败');
  });
});
