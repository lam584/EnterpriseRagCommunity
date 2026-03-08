import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearApiBaseUrlForTests,
  getFetchCallInfo,
  installFetchMock,
  resetServiceTest,
  setApiBaseUrlForTests,
} from '../testUtils/serviceTestHarness';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('retrievalVectorIndexService', () => {
  beforeEach(() => {
    resetServiceTest();
    clearApiBaseUrlForTests();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('adminListVectorIndices uses defaults and supports overriding page/size', async () => {
    const { adminListVectorIndices } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 1, totalElements: 0, size: 50, number: 0 } });
    await expect(adminListVectorIndices()).resolves.toMatchObject({ totalElements: 0 });
    const info1 = getFetchCallInfo(lastCall())!;
    const u1 = parseUrl(info1.url);
    expect(u1.pathname).toBe('/api/admin/retrieval/vector-indices');
    expect(u1.searchParams.get('page')).toBe('0');
    expect(u1.searchParams.get('size')).toBe('50');

    setApiBaseUrlForTests('https://api.example');
    replyJsonOnce({ ok: true, json: { content: [], totalPages: 1, totalElements: 0, size: 50, number: 0 } });
    await expect(adminListVectorIndices()).resolves.toMatchObject({ totalElements: 0 });
    expect(String(lastCall()?.[0] || '')).toContain('https://api.example/api/admin/retrieval/vector-indices');

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 1, totalElements: 0, size: 20, number: 3 } });
    await expect(adminListVectorIndices({ page: 3, size: 20 })).resolves.toMatchObject({ number: 3 });
    const info2 = getFetchCallInfo(lastCall())!;
    const u2 = parseUrl(info2.url);
    expect(u2.searchParams.get('page')).toBe('3');
    expect(u2.searchParams.get('size')).toBe('20');
    expect(info2.init).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('adminListVectorIndices throws backend message and falls back on json parse failure', async () => {
    const { adminListVectorIndices } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListVectorIndices()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminListVectorIndices()).rejects.toThrow('获取向量索引列表失败');
  });

  it('adminCreateVectorIndex includes csrf header only when token is non-empty', async () => {
    const { adminCreateVectorIndex } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    getCsrfTokenMock.mockResolvedValueOnce('csrf-1');
    replyJsonOnce({ ok: true, json: { id: 1 } });
    await expect(
      adminCreateVectorIndex({ provider: 'FAISS', collectionName: 'c', metric: 'L2', dim: 3, status: 'READY' } as any),
    ).resolves.toMatchObject({ id: 1 });
    const headers1 = (lastCall()?.[1]?.headers || {}) as Record<string, unknown>;
    expect(headers1).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-1' });

    getCsrfTokenMock.mockResolvedValueOnce('');
    replyJsonOnce({ ok: true, json: { id: 2 } });
    await expect(
      adminCreateVectorIndex({ provider: 'FAISS', collectionName: 'c', metric: 'L2', dim: 3, status: 'READY' } as any),
    ).resolves.toMatchObject({ id: 2 });
    const headers2 = (lastCall()?.[1]?.headers || {}) as Record<string, unknown>;
    expect(headers2).toMatchObject({ 'Content-Type': 'application/json' });
    expect(headers2).not.toHaveProperty('X-XSRF-TOKEN');
  });

  it('adminCreateVectorIndex throws backend message and falls back on json parse failure', async () => {
    const { adminCreateVectorIndex } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminCreateVectorIndex({ provider: 'FAISS', collectionName: 'c', metric: 'L2', dim: 3, status: 'READY' } as any)).rejects.toThrow(
      'bad',
    );

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminCreateVectorIndex({ provider: 'FAISS', collectionName: 'c', metric: 'L2', dim: 3, status: 'READY' } as any)).rejects.toThrow(
      '创建向量索引失败',
    );
  });

  it('adminUpdateVectorIndex omits csrf header when token is empty', async () => {
    const { adminUpdateVectorIndex } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    getCsrfTokenMock.mockResolvedValueOnce('');
    replyJsonOnce({ ok: true, json: { id: 1 } });
    await adminUpdateVectorIndex({ id: 1, status: 'READY' } as any);
    const headers = (lastCall()?.[1]?.headers || {}) as Record<string, unknown>;
    expect(headers).toMatchObject({ 'Content-Type': 'application/json' });
    expect(headers).not.toHaveProperty('X-XSRF-TOKEN');
  });

  it('adminUpdateVectorIndex prefers backend message and falls back on json parse failure', async () => {
    const { adminUpdateVectorIndex } = await import('./retrievalVectorIndexService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminUpdateVectorIndex({ id: 1, status: 'READY' } as any)).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpdateVectorIndex({ id: 1, status: 'READY' } as any)).rejects.toThrow('更新向量索引失败');
  });

  it('adminDeleteVectorIndex returns void on 404 and throws on other non-ok', async () => {
    const { adminDeleteVectorIndex } = await import('./retrievalVectorIndexService');
    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();

    replyOnce({ ok: false, status: 404, json: { message: 'not found' } });
    await expect(adminDeleteVectorIndex(1)).resolves.toBeUndefined();
    expect(getFetchCallInfo(lastCall())?.method).toBe('DELETE');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminDeleteVectorIndex(2)).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminDeleteVectorIndex(3)).rejects.toThrow('删除向量索引失败');
  });

  it('adminBuildPostRagIndex builds query params including clear and chunkOverlapChars', async () => {
    const { adminBuildPostRagIndex } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { successChunks: 1 } });
    await expect(
      adminBuildPostRagIndex({
        id: 1,
        boardId: 2,
        fromPostId: 3,
        postBatchSize: 10,
        chunkMaxChars: 2000,
        chunkOverlapChars: 50,
        clear: true,
        embeddingModel: 'm',
        embeddingProviderId: 'p',
        embeddingDims: 3,
      }),
    ).resolves.toMatchObject({ successChunks: 1 });

    const info = getFetchCallInfo(lastCall())!;
    expect(info.method).toBe('POST');
    expect(info.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-token' });

    const u = parseUrl(info.url);
    expect(u.pathname).toBe('/api/admin/retrieval/vector-indices/1/build/posts');
    expect(u.searchParams.get('boardId')).toBe('2');
    expect(u.searchParams.get('fromPostId')).toBe('3');
    expect(u.searchParams.get('postBatchSize')).toBe('10');
    expect(u.searchParams.get('chunkMaxChars')).toBe('2000');
    expect(u.searchParams.get('chunkOverlapChars')).toBe('50');
    expect(u.searchParams.get('clear')).toBe('true');
    expect(u.searchParams.get('embeddingModel')).toBe('m');
    expect(u.searchParams.get('embeddingProviderId')).toBe('p');
    expect(u.searchParams.get('embeddingDims')).toBe('3');
  });

  it('adminBuildPostRagIndex omits falsy numeric params but keeps chunkOverlapChars=0', async () => {
    const { adminBuildPostRagIndex } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { successChunks: 1 } });
    await adminBuildPostRagIndex({
      id: 9,
      boardId: 0,
      fromPostId: 0,
      postBatchSize: 0,
      chunkMaxChars: 0,
      chunkOverlapChars: 0,
      clear: false,
      embeddingModel: 'm',
      embeddingProviderId: 'p',
      embeddingDims: 0,
    });

    const u = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u.searchParams.has('boardId')).toBe(false);
    expect(u.searchParams.has('fromPostId')).toBe(false);
    expect(u.searchParams.has('postBatchSize')).toBe(false);
    expect(u.searchParams.has('chunkMaxChars')).toBe(false);
    expect(u.searchParams.has('clear')).toBe(false);
    expect(u.searchParams.has('embeddingDims')).toBe(false);
    expect(u.searchParams.get('chunkOverlapChars')).toBe('0');
    expect(u.searchParams.get('embeddingModel')).toBe('m');
    expect(u.searchParams.get('embeddingProviderId')).toBe('p');
  });

  it('adminBuildPostRagIndex omits chunkOverlapChars when null and throws backend message on error', async () => {
    const { adminBuildPostRagIndex } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { successChunks: 1 } });
    await adminBuildPostRagIndex({ id: 1, chunkOverlapChars: null as any });
    const u1 = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u1.searchParams.has('chunkOverlapChars')).toBe(false);

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminBuildPostRagIndex({ id: 1 })).rejects.toThrow('bad');
  });

  it('adminRebuildPostRagIndex and adminSyncPostRagIndex throw their fallback messages', async () => {
    const { adminRebuildPostRagIndex, adminSyncPostRagIndex } = await import('./retrievalVectorIndexService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminRebuildPostRagIndex({ id: 1 } as any)).rejects.toThrow('全量重建失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminSyncPostRagIndex({ id: 1 } as any)).rejects.toThrow('增量同步失败');
  });

  it('adminGetRagAutoSyncConfig and adminUpdateRagAutoSyncConfig cover ok and error message', async () => {
    const { adminGetRagAutoSyncConfig, adminUpdateRagAutoSyncConfig } = await import('./retrievalVectorIndexService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { enabled: true, intervalSeconds: 30 } });
    await expect(adminGetRagAutoSyncConfig()).resolves.toMatchObject({ intervalSeconds: 30 });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm2' } });
    await expect(adminUpdateRagAutoSyncConfig({ enabled: false })).rejects.toThrow('m2');
  });

  it('adminTestQueryPostRagIndex posts payload and throws fallback on json parse failure', async () => {
    const { adminTestQueryPostRagIndex } = await import('./retrievalVectorIndexService');
    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { indexName: 'idx' } });
    await expect(adminTestQueryPostRagIndex({ id: 1, payload: { queryText: 'q', topK: 3 } })).resolves.toMatchObject({ indexName: 'idx' });
    const info = getFetchCallInfo(lastCall())!;
    expect(info.method).toBe('POST');
    expect(info.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
    expect(info.body).toBe(JSON.stringify({ queryText: 'q', topK: 3 }));

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminTestQueryPostRagIndex({ id: 1, payload: { queryText: 'q' } })).rejects.toThrow('测试查询失败');
  });
});
