import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearApiBaseUrlForTests, getFetchCallInfo, installFetchMock, resetServiceTest, setApiBaseUrlForTests } from '../testUtils/serviceTestHarness';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('postComposeAiSnapshotService', () => {
  beforeEach(() => {
    resetServiceTest();
    clearApiBaseUrlForTests();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('createPostComposeAiSnapshot posts payload with csrf header and supports api base url', async () => {
    const { createPostComposeAiSnapshot } = await import('./postComposeAiSnapshotService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { id: 1, targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1, status: 'PENDING' } });
    await expect(
      createPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 1, beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1 }),
    ).resolves.toMatchObject({ id: 1 });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('POST');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
    expect(parseUrl(info1.url).pathname).toBe('/api/post-compose/ai-snapshots');

    setApiBaseUrlForTests('https://api.example');
    replyJsonOnce({ ok: true, json: { id: 2, targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1, status: 'PENDING' } });
    await expect(
      createPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 1, beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1 }),
    ).resolves.toMatchObject({ id: 2 });
    expect(String(lastCall()?.[0] || '')).toContain('https://api.example/api/post-compose/ai-snapshots');
  });

  it('createPostComposeAiSnapshot throws backend message/error and falls back on json parse failure', async () => {
    const { createPostComposeAiSnapshot } = await import('./postComposeAiSnapshotService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm' } });
    await expect(
      createPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 1, beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1 }),
    ).rejects.toThrow('m');

    replyJsonOnce({ ok: false, status: 400, json: { error: 'e' } });
    await expect(
      createPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 1, beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1 }),
    ).rejects.toThrow('e');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(
      createPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 1, beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1 }),
    ).rejects.toThrow('创建快照失败');
  });

  it('getPendingPostComposeAiSnapshot builds query, returns null on 404, and falls back on json parse failure', async () => {
    const { getPendingPostComposeAiSnapshot } = await import('./postComposeAiSnapshotService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { id: 1, targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1, status: 'PENDING' } });
    await expect(getPendingPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 1, postId: 2 })).resolves.toMatchObject({ id: 1 });
    const u1 = parseUrl(String(lastCall()?.[0] || ''));
    expect(u1.pathname).toBe('/api/post-compose/ai-snapshots/pending');
    expect(u1.searchParams.get('targetType')).toBe('DRAFT');
    expect(u1.searchParams.get('draftId')).toBe('1');
    expect(u1.searchParams.get('postId')).toBe('2');

    replyOnce({ ok: false, status: 404, json: { message: 'not found' } });
    await expect(getPendingPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 1 })).resolves.toBeNull();

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(getPendingPostComposeAiSnapshot({ targetType: 'DRAFT', draftId: 1 })).rejects.toThrow('加载待处理快照失败');
  });

  it('applyPostComposeAiSnapshot and revertPostComposeAiSnapshot cover ok and fallback errors', async () => {
    const { applyPostComposeAiSnapshot, revertPostComposeAiSnapshot } = await import('./postComposeAiSnapshotService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { id: 1, targetType: 'DRAFT', beforeTitle: 't', beforeContent: 'c', beforeBoardId: 1, status: 'APPLIED' } });
    await expect(applyPostComposeAiSnapshot(1, 'after')).resolves.toMatchObject({ status: 'APPLIED' });

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(revertPostComposeAiSnapshot(1)).rejects.toThrow('回滚快照失败');
  });
});

