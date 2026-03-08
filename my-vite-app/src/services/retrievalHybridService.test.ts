import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import {
  adminGetHybridRetrievalConfig,
  adminListHybridRetrievalEvents,
  adminListHybridRetrievalHits,
  adminTestHybridRerank,
  adminTestHybridRetrieval,
  adminUpdateHybridRetrievalConfig,
} from './retrievalHybridService';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('retrievalHybridService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('adminGetHybridRetrievalConfig and adminUpdateHybridRetrievalConfig cover ok and fallback', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, fusionMode: 'RRF' } });
    await expect(adminGetHybridRetrievalConfig()).resolves.toMatchObject({ fusionMode: 'RRF' });
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });

    const fetchMock2 = mockFetchResponseOnce({ ok: true, json: { enabled: false } });
    await expect(adminUpdateHybridRetrievalConfig({ enabled: false })).resolves.toMatchObject({ enabled: false });
    expect(fetchMock2.mock.calls[0]?.[1]).toMatchObject({ method: 'PUT', credentials: 'include', headers: { 'X-XSRF-TOKEN': 'csrf' } });

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminUpdateHybridRetrievalConfig({})).rejects.toThrow('保存 Hybrid 检索配置失败');
  });

  it('adminTestHybridRetrieval and adminTestHybridRerank cover ok and fallback', async () => {
    mockFetchResponseOnce({ ok: true, json: { queryText: 'q', finalHits: [] } });
    await expect(adminTestHybridRetrieval({ queryText: 'q' })).resolves.toMatchObject({ queryText: 'q' });

    mockFetchResponseOnce({ ok: true, json: { queryText: 'q', ok: true, results: [] } });
    await expect(adminTestHybridRerank({ queryText: 'q', documents: [] })).resolves.toMatchObject({ ok: true });

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminTestHybridRetrieval({ queryText: 'q' })).rejects.toThrow('Hybrid 检索测试失败');
  });

  it('adminListHybridRetrievalEvents and adminListHybridRetrievalHits cover ok and errors', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [], totalElements: 0 } });
    await expect(adminListHybridRetrievalEvents({ from: 'a', to: 'b' })).resolves.toMatchObject({ totalElements: 0 });
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/retrieval/hybrid/logs/events?');
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(url).toContain('from=a');
    expect(url).toContain('to=b');

    mockFetchResponseOnce({ ok: true, json: [{ id: 1, eventId: 9 }] });
    await expect(adminListHybridRetrievalHits(9)).resolves.toMatchObject([{ eventId: 9 }]);

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListHybridRetrievalEvents({})).rejects.toThrow('bad');
  });

  it('covers fallback errors for missing/non-string backend message', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: { message: 1 } });
    await expect(adminGetHybridRetrievalConfig()).rejects.toThrow('获取 Hybrid 检索配置失败');

    mockFetchResponseOnce({ ok: false, status: 500, json: { message: 1 } });
    await expect(adminTestHybridRerank({ queryText: 'q', documents: [] })).rejects.toThrow('重排模型测试失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad-json') });
    await expect(adminListHybridRetrievalEvents({})).rejects.toThrow('获取检索日志失败');

    mockFetchResponseOnce({ ok: false, status: 500, json: { message: 1 } });
    await expect(adminListHybridRetrievalHits(1)).rejects.toThrow('获取检索命中详情失败');
  });

  it('apiUrl 默认使用相对路径', async () => {
    const svc = await import('./retrievalHybridService');
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true } });
    await expect(svc.adminGetHybridRetrievalConfig()).resolves.toMatchObject({ enabled: true });
    expect(String(fetchMock.mock.calls[0]?.[0] || '')).toContain('/api/admin/retrieval/hybrid/config');
  });
});
