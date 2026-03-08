import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockConsole } from '../testUtils/mockConsole';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { clearApiBaseUrlForTests, setApiBaseUrlForTests } from '../testUtils/serviceTestHarness';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import { adminGetTokenMetrics, adminGetTokenTimeline, adminListLlmPriceConfigs, adminListTokenSources, adminUpsertLlmPriceConfig } from './tokenMetricsAdminService';

describe('tokenMetricsAdminService', () => {
  let consoleMock: ReturnType<typeof mockConsole>;

  beforeEach(() => {
    vi.restoreAllMocks();
    consoleMock = mockConsole();
    clearApiBaseUrlForTests();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  afterEach(() => {
    consoleMock.restore();
  });

  it('adminGetTokenMetrics builds query and sends GET with credentials', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: { start: 's', end: 'e', totalTokens: 1, totalCost: 0, items: [] },
    });

    const res = await adminGetTokenMetrics({ start: '2026-01-01', end: '', source: 'portal', pricingMode: undefined });

    expect(res.totalTokens).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/metrics/token');
    expect(url).toContain('start=2026-01-01');
    expect(url).toContain('source=portal');
    expect(url).not.toContain('end=');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('adminGetTokenMetrics throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetTokenMetrics({})).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminGetTokenMetrics({})).rejects.toThrow('获取 Token 成本统计失败');
  });

  it('adminUpsertLlmPriceConfig sends PUT with csrf header and body', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { id: 1, name: 'n', currency: 'CNY' } });

    const res = await adminUpsertLlmPriceConfig({ name: 'n', currency: 'CNY' });

    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toContain('/api/admin/ai/prices');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({ name: 'n', currency: 'CNY' }),
    });
    expect(res.id).toBe(1);
  });

  it('adminUpsertLlmPriceConfig throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpsertLlmPriceConfig({ name: 'n' })).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminUpsertLlmPriceConfig({ name: 'n' })).rejects.toThrow('保存模型单价配置失败');
  });

  it('adminGetTokenTimeline builds query and throws fallback on failure', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: { start: 's', end: 'e', source: 'portal', bucket: 'DAY', totalTokens: 1, points: [] },
    });
    await expect(adminGetTokenTimeline({ start: 's', end: 'e', source: 'portal', bucket: 'DAY' })).resolves.toMatchObject({ totalTokens: 1 });
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/metrics/token/timeline');
    expect(url).toContain('bucket=DAY');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminGetTokenTimeline({})).rejects.toThrow('获取 Token 趋势失败');
  });

  it('adminListTokenSources and adminListLlmPriceConfigs cover ok and error messages', async () => {
    mockFetchResponseOnce({ ok: true, json: [{ taskType: 't', label: 'l', category: 'c', sortIndex: 1 }] });
    await expect(adminListTokenSources()).resolves.toMatchObject([{ taskType: 't' }]);

    mockFetchResponseOnce({ ok: true, json: [{ id: 1, name: 'n', currency: 'CNY' }] });
    await expect(adminListLlmPriceConfigs()).resolves.toMatchObject([{ id: 1 }]);

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListTokenSources()).rejects.toThrow('bad');
  });

  it('adminListTokenSources uses api base url and falls back on json parse failure', async () => {
    setApiBaseUrlForTests('https://api.example');
    const fetchMock = mockFetchResponseOnce({ ok: true, json: [] });
    await expect(adminListTokenSources()).resolves.toEqual([]);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('https://api.example/api/admin/metrics/token/sources');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminListTokenSources()).rejects.toThrow('获取场景列表失败');
  });
});
