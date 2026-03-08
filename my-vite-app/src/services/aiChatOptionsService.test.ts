import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getAiChatOptions } from './aiChatOptionsService';

describe('aiChatOptionsService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('getAiChatOptions sends GET and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { activeProviderId: 'p1', providers: [{ id: 'p1', name: 'P', chatModels: [{ name: 'm', isDefault: true }] }] } });

    const res = await getAiChatOptions();

    expect(res.activeProviderId).toBe('p1');
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/ai/chat/options');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getAiChatOptions throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getAiChatOptions()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getAiChatOptions()).rejects.toThrow('获取模型选项失败');
  });

  it('getAiChatOptions covers json parse fallback', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getAiChatOptions()).resolves.toEqual({});

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(getAiChatOptions()).rejects.toThrow('获取模型选项失败');
  });
});
