import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { adminProbeModel } from './aiModelProbeAdminService';

describe('aiModelProbeAdminService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('adminProbeModel sends GET with query params', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { ok: true, modelName: 'm' } });
    const controller = new AbortController();

    const res = await adminProbeModel('CHAT', 'p', 'm', { timeoutMs: 1000, signal: controller.signal });

    expect(res.ok).toBe(true);
    const url = fetchMock.mock.calls[0]?.[0] as string;
    expect(url).toContain('/api/admin/ai/models/probe?');
    expect(url).toContain('kind=CHAT');
    expect(url).toContain('providerId=p');
    expect(url).toContain('modelName=m');
    expect(url).toContain('timeoutMs=1000');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include', signal: controller.signal });
  });

  it('adminProbeModel keeps timeoutMs=0 in query', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { ok: true } });
    await expect(adminProbeModel('CHAT', 'p', 'm', { timeoutMs: 0 })).resolves.toMatchObject({ ok: true });
    const url = fetchMock.mock.calls[0]?.[0] as string;
    expect(url).toContain('timeoutMs=0');
  });

  it('adminProbeModel throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminProbeModel('CHAT', 'p', 'm')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(adminProbeModel('CHAT', 'p', 'm')).rejects.toThrow('模型探活失败');
  });

  it('adminProbeModel falls back when response json fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(adminProbeModel('CHAT', 'p', 'm')).rejects.toThrow('模型探活失败');
  });
});
