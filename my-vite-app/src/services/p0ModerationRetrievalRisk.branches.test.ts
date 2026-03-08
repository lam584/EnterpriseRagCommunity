import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('p0ModerationRetrievalRisk branches', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('retrievalHybridService throws backend message and falls back on json parse failure', async () => {
    const { adminGetHybridRetrievalConfig, adminUpdateHybridRetrievalConfig, adminListHybridRetrievalEvents } = await import('./retrievalHybridService');
    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminGetHybridRetrievalConfig()).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(adminUpdateHybridRetrievalConfig({ enabled: true })).rejects.toThrow('保存 Hybrid 检索配置失败');

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, size: 0, number: 0 } });
    await adminListHybridRetrievalEvents({ from: '2020-01-01', to: '2020-02-01' });
    const url = getFetchCallInfo(lastCall())?.url || '';
    expect(url).toContain('/api/admin/retrieval/hybrid/logs/events?');
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(url).toContain('from=2020-01-01');
    expect(url).toContain('to=2020-02-01');
  });

  it('retrievalContextService includes from/to params and uses defaults', async () => {
    const { adminListContextWindows } = await import('./retrievalContextService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, size: 0, number: 0 } });
    await adminListContextWindows({ from: '2020-01-01', to: '2020-02-01' });

    const url = getFetchCallInfo(lastCall())?.url || '';
    expect(url).toContain('/api/admin/retrieval/context/logs/windows?');
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(url).toContain('from=2020-01-01');
    expect(url).toContain('to=2020-02-01');
  });

  it('moderationChunkReviewConfigService throws backend message and falls back on json parse failure', async () => {
    const { getModerationChunkReviewConfig, updateModerationChunkReviewConfig } = await import('./moderationChunkReviewConfigService');
    const { replyOnce, replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm2' } });
    await expect(getModerationChunkReviewConfig()).rejects.toThrow('m2');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(getModerationChunkReviewConfig()).rejects.toThrow('加载分片审核配置失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(updateModerationChunkReviewConfig({ enabled: true } as any)).rejects.toThrow('保存分片审核配置失败');
  });

  it('moderationChunkReviewLogsService builds query params and throws default error', async () => {
    const { adminListModerationChunkLogs } = await import('./moderationChunkReviewLogsService');
    const { replyOnce, lastCall } = installFetchMock();

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(
      adminListModerationChunkLogs({ limit: 10, queueId: 1, status: 'DONE', verdict: 'APPROVE', sourceType: 'POST', fileAssetId: 2, keyword: 'k' }),
    ).rejects.toThrow('加载最近分片结果失败');

    const url = getFetchCallInfo(lastCall())?.url || '';
    expect(url).toContain('/api/admin/moderation/chunk-review/logs?');
    expect(url).toContain('limit=10');
    expect(url).toContain('queueId=1');
    expect(url).toContain('status=DONE');
    expect(url).toContain('verdict=APPROVE');
    expect(url).toContain('sourceType=POST');
    expect(url).toContain('fileAssetId=2');
    expect(url).toContain('keyword=k');
  });

  it('moderationChunkReviewLogsService covers success, backend message, and detail/content branches', async () => {
    const { adminListModerationChunkLogs, adminGetModerationChunkLogDetail, adminGetModerationChunkLogContent } = await import('./moderationChunkReviewLogsService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: [] });
    await expect(adminListModerationChunkLogs({})).resolves.toEqual([]);
    const listUrl = getFetchCallInfo(lastCall())?.url || '';
    expect(listUrl).toContain('/api/admin/moderation/chunk-review/logs?');
    expect(listUrl).not.toContain('limit=');
    expect(listUrl).not.toContain('queueId=');
    expect(listUrl).not.toContain('status=');
    expect(listUrl).not.toContain('verdict=');
    expect(listUrl).not.toContain('sourceType=');
    expect(listUrl).not.toContain('fileAssetId=');
    expect(listUrl).not.toContain('keyword=');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListModerationChunkLogs({ keyword: 'k' })).rejects.toThrow('bad');

    replyJsonOnce({ ok: true, json: { chunk: { id: 1, chunkSetId: 2, queueId: 3 }, chunkSet: { id: 2, queueId: 3 } } });
    await expect(adminGetModerationChunkLogDetail(1)).resolves.toMatchObject({ chunk: { id: 1 }, chunkSet: { id: 2 } });

    replyJsonOnce({ ok: false, status: 500, json: {} });
    await expect(adminGetModerationChunkLogDetail(1)).rejects.toThrow('加载分片详情失败');

    replyJsonOnce({ ok: true, json: { text: 'x' } });
    const controller = new AbortController();
    await expect(adminGetModerationChunkLogContent(1, controller.signal)).resolves.toMatchObject({ text: 'x' });
    expect(getFetchCallInfo(lastCall())?.init?.signal).toBe(controller.signal);
  });

  it('retrievalCitationService and retrievalVectorIndexService cover error/default branches', async () => {
    const csrf = await import('../utils/csrfUtils');
    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm3' } });
    const { adminGetCitationConfig } = await import('./retrievalCitationService');
    await expect(adminGetCitationConfig()).rejects.toThrow('m3');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    const { adminTestCitation } = await import('./retrievalCitationService');
    await expect(adminTestCitation({ items: [] })).rejects.toThrow('引用配置测试失败');

    (csrf as any).getCsrfToken.mockResolvedValueOnce('');
    replyJsonOnce({ ok: true, json: { id: 1 } });
    const { adminCreateVectorIndex, adminDeleteVectorIndex } = await import('./retrievalVectorIndexService');
    await adminCreateVectorIndex({ name: 'n', indexName: 'i' } as any);
    const createInfo = getFetchCallInfo(lastCall());
    expect(createInfo?.method).toBe('POST');
    expect((createInfo?.init as any)?.headers?.['X-XSRF-TOKEN']).toBeUndefined();

    replyOnce({ ok: false, status: 404, json: {} });
    await expect(adminDeleteVectorIndex(1)).resolves.toBeUndefined();
  });

  it('retrievalCitationService covers success and default-message branches', async () => {
    (import.meta as any).env = { ...((import.meta as any).env || {}), VITE_API_BASE_URL: 'http://x' };
    vi.resetModules();
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminGetCitationConfig, adminUpdateCitationConfig, adminTestCitation } = await import('./retrievalCitationService');

    replyJsonOnce({ ok: true, json: { enabled: true, citationMode: 'BOTH' } });
    await expect(adminGetCitationConfig()).resolves.toMatchObject({ citationMode: 'BOTH' });
    expect(String(lastCall()?.[0] || '')).toContain('/api/admin/retrieval/citation/config');

    replyJsonOnce({ ok: true, json: { enabled: false } });
    await expect(adminUpdateCitationConfig({ enabled: false })).resolves.toMatchObject({ enabled: false });

    replyJsonOnce({ ok: true, json: { sourcesPreview: 's' } });
    await expect(adminTestCitation({ items: [] })).resolves.toMatchObject({ sourcesPreview: 's' });

    replyOnce({ ok: false, status: 400, json: {} });
    await expect(adminGetCitationConfig()).rejects.toThrow('获取引用配置失败');

    (import.meta as any).env = { ...((import.meta as any).env || {}), VITE_API_BASE_URL: '' };
    vi.resetModules();
  });

  it('moderationEmbedSamplesService covers query filtering, 204 handling, and request failure branches', async () => {
    (import.meta as any).env = { ...((import.meta as any).env || {}), VITE_API_BASE_URL: 'http://x' };
    vi.resetModules();

    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();
    const { listSamples, createSample, deleteSample, syncSamplesIncremental } = await import('./moderationEmbedSamplesService');

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 10 } });
    await listSamples({ page: 1, pageSize: 10, category: '', enabled: '' });
    const listUrl = getFetchCallInfo(lastCall())?.url || '';
    expect(listUrl).toContain('/api/admin/moderation/embed/samples?');
    expect(listUrl).toContain('page=1');
    expect(listUrl).toContain('pageSize=10');
    expect(listUrl).not.toContain('category=');
    expect(listUrl).not.toContain('enabled=');

    replyJsonOnce({ ok: true, json: { id: 1 } });
    await createSample({ category: 'AD_SAMPLE', rawText: 't' } as any);
    const createInfo = getFetchCallInfo(lastCall())!;
    expect(createInfo.method).toBe('POST');
    expect(createInfo.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(deleteSample(1)).resolves.toBeUndefined();
    const delInfo = getFetchCallInfo(lastCall())!;
    expect(delInfo.method).toBe('DELETE');
    expect((delInfo.headers as any)?.['Content-Type']).toBeUndefined();

    replyJsonOnce({ ok: true, json: { total: 0 } });
    await syncSamplesIncremental({ onlyEnabled: false, fromId: 0, batchSize: 0 });
    const syncUrl = getFetchCallInfo(lastCall())?.url || '';
    expect(syncUrl).toContain('onlyEnabled=false');
    expect(syncUrl).toContain('fromId=0');
    expect(syncUrl).toContain('batchSize=0');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(deleteSample(2)).rejects.toThrow('请求失败: 500');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(listSamples({ page: 1, pageSize: 10 })).rejects.toThrow('bad');

    (import.meta as any).env = { ...((import.meta as any).env || {}), VITE_API_BASE_URL: '' };
    vi.resetModules();
  });
});
