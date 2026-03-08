import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('moderationQueueService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('adminListModerationQueue applies default pagination and omits empty query fields', async () => {
    const { adminListModerationQueue } = await import('./moderationQueueService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } });

    await expect(adminListModerationQueue({ status: '' as any })).resolves.toMatchObject({ content: [] });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('/api/admin/moderation/queue?');
    expect(info?.url).toContain('page=1');
    expect(info?.url).toContain('pageSize=20');
    expect(info?.url).not.toContain('status=');
    expect(info?.method).toBe('GET');
    expect(info?.init?.credentials).toBe('include');
  });

  it('adminGetModerationQueueChunkProgress sets includeChunks/limit defaults and boundaries', async () => {
    const { adminGetModerationQueueChunkProgress } = await import('./moderationQueueService');
    const { replyOnce, fetchMock } = installFetchMock();

    replyOnce({ ok: true, json: { queueId: 1, status: 'OK' } });
    await expect(adminGetModerationQueueChunkProgress(1)).resolves.toMatchObject({ queueId: 1 });

    replyOnce({ ok: true, json: { queueId: 2, status: 'OK' } });
    await expect(adminGetModerationQueueChunkProgress(2, { includeChunks: true, limit: 0 })).resolves.toMatchObject({ queueId: 2 });

    expect(fetchMock).toHaveBeenCalledTimes(2);

    const info1 = getFetchCallInfo(fetchMock.mock.calls[0] as any);
    expect(info1?.url).toContain('/api/admin/moderation/queue/1/chunk-progress?');
    expect(info1?.url).toContain('includeChunks=0');
    expect(info1?.url).toContain('limit=80');
    expect(info1?.method).toBe('GET');
    expect(info1?.init?.credentials).toBe('include');

    const info2 = getFetchCallInfo(fetchMock.mock.calls[1] as any);
    expect(info2?.url).toContain('/api/admin/moderation/queue/2/chunk-progress?');
    expect(info2?.url).toContain('includeChunks=1');
    expect(info2?.url).toContain('limit=0');
  });

  it('adminBatchRequeueModerationQueue sends csrf header, credentials and ids fallback', async () => {
    const { adminBatchRequeueModerationQueue } = await import('./moderationQueueService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { total: 1, success: 1, failed: 0, successIds: [1] } });

    await expect((adminBatchRequeueModerationQueue as any)(undefined, 'r')).resolves.toMatchObject({ success: 1 });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('/api/admin/moderation/queue/batch/requeue');
    expect(info?.method).toBe('POST');
    expect(info?.init?.credentials).toBe('include');
    expect(info?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(info?.body).toBe(JSON.stringify({ ids: [], reason: 'r' }));
  });

  it('adminSetModerationQueueRiskTags throws backend message on not ok', async () => {
    const { adminSetModerationQueueRiskTags } = await import('./moderationQueueService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminSetModerationQueueRiskTags(1, ['a'])).rejects.toThrow('bad');
  });

  it('adminListModerationQueue keeps numeric 0 values and omits null/empty', async () => {
    const { adminListModerationQueue } = await import('./moderationQueueService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } });

    await expect(adminListModerationQueue({ id: 0 as any, boardId: null as any, status: '' as any, assignedToId: undefined })).resolves.toMatchObject({
      content: [],
    });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('id=0');
    expect(info?.url).not.toContain('boardId=');
    expect(info?.url).not.toContain('status=');
    expect(info?.url).not.toContain('assignedToId=');
  });

  it('adminGetModerationQueueDetail covers backend message, default fallback, and json-parse-failure branches', async () => {
    const { adminGetModerationQueueDetail } = await import('./moderationQueueService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 400, json: { message: 'nope' } });
    await expect(adminGetModerationQueueDetail(1)).rejects.toThrow('nope');

    replyOnce({ ok: false, status: 500, json: { message: 1 } });
    await expect(adminGetModerationQueueDetail(2)).rejects.toThrow('获取审核任务详情失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad-json') });
    await expect(adminGetModerationQueueDetail(3)).rejects.toThrow('获取审核任务详情失败');
  });

  it('adminGetModerationQueueRiskTags falls back to empty array on null payload', async () => {
    const { adminGetModerationQueueRiskTags } = await import('./moderationQueueService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: true, json: null });
    await expect(adminGetModerationQueueRiskTags(1)).resolves.toEqual([]);
  });

  it('covers multiple moderation queue action endpoints request shapes', async () => {
    const {
      adminApproveModerationQueue,
      adminOverrideApproveModerationQueue,
      adminRejectModerationQueue,
      adminOverrideRejectModerationQueue,
      adminClaimModerationQueue,
      adminReleaseModerationQueue,
      adminRequeueModerationQueue,
      adminToHumanModerationQueue,
      adminBanModerationQueueUser,
      adminBackfillModerationQueue,
    } = await import('./moderationQueueService');
    const { replyOnce, fetchMock } = installFetchMock();

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminApproveModerationQueue(1, 'r')).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminOverrideApproveModerationQueue(1, 'r')).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminRejectModerationQueue(1, 'r')).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminOverrideRejectModerationQueue(1, 'r')).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminClaimModerationQueue(1)).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminReleaseModerationQueue(1)).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminRequeueModerationQueue(1, 'r')).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminToHumanModerationQueue(1, 'r')).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { id: 1 } });
    await expect(adminBanModerationQueueUser(1, 'r')).resolves.toMatchObject({ id: 1 });

    replyOnce({ ok: true, json: { scannedPosts: 0, scannedComments: 0, alreadyQueued: 0, enqueued: 0, skipped: 0 } });
    await expect(adminBackfillModerationQueue({ dryRun: true, limit: 1 })).resolves.toMatchObject({ skipped: 0 });

    for (const call of fetchMock.mock.calls as any[]) {
      const info = getFetchCallInfo(call);
      expect(info?.method).toBe('POST');
      expect(info?.init?.credentials).toBe('include');
      expect(info?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    }
  });

  it('apiUrl 默认使用相对路径', async () => {
    const { adminGetModerationQueueDetail } = await import('./moderationQueueService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { id: 1 } });

    await expect(adminGetModerationQueueDetail(1)).resolves.toMatchObject({ id: 1 });
    expect(String(lastCall()?.[0] || '')).toContain('/api/admin/moderation/queue/1');
  });

  it('covers fallback error messages for non-ok responses without backend message', async () => {
    const {
      adminListModerationQueue,
      adminGetModerationQueueChunkProgress,
      adminBatchRequeueModerationQueue,
      adminGetModerationQueueRiskTags,
      adminSetModerationQueueRiskTags,
      adminApproveModerationQueue,
      adminOverrideApproveModerationQueue,
      adminRejectModerationQueue,
      adminOverrideRejectModerationQueue,
      adminBanModerationQueueUser,
      adminBackfillModerationQueue,
      adminClaimModerationQueue,
      adminReleaseModerationQueue,
      adminRequeueModerationQueue,
      adminToHumanModerationQueue,
    } = await import('./moderationQueueService');
    const { replyOnce } = installFetchMock();

    const cases: Array<[string, () => Promise<unknown>, string]> = [
      ['adminListModerationQueue', () => adminListModerationQueue(), '获取审核队列失败'],
      ['adminGetModerationQueueChunkProgress', () => adminGetModerationQueueChunkProgress(1), '获取分片进度失败'],
      ['adminBatchRequeueModerationQueue', () => adminBatchRequeueModerationQueue([1], 'r'), '批量进入再次审核失败'],
      ['adminGetModerationQueueRiskTags', () => adminGetModerationQueueRiskTags(1), '获取风险标签失败'],
      ['adminSetModerationQueueRiskTags', () => adminSetModerationQueueRiskTags(1, ['a']), '设置风险标签失败'],
      ['adminApproveModerationQueue', () => adminApproveModerationQueue(1, 'r'), '审核通过失败'],
      ['adminOverrideApproveModerationQueue', () => adminOverrideApproveModerationQueue(1, 'r'), '覆核通过失败'],
      ['adminRejectModerationQueue', () => adminRejectModerationQueue(1, 'r'), '驳回失败'],
      ['adminOverrideRejectModerationQueue', () => adminOverrideRejectModerationQueue(1, 'r'), '覆核驳回失败'],
      ['adminBanModerationQueueUser', () => adminBanModerationQueueUser(1, 'r'), '封禁用户失败'],
      ['adminBackfillModerationQueue', () => adminBackfillModerationQueue({ limit: 1 }), '补齐历史待审数据失败'],
      ['adminClaimModerationQueue', () => adminClaimModerationQueue(1), '认领失败'],
      ['adminReleaseModerationQueue', () => adminReleaseModerationQueue(1), '释放失败'],
      ['adminRequeueModerationQueue', () => adminRequeueModerationQueue(1, 'r'), '重新进入自动审核失败'],
      ['adminToHumanModerationQueue', () => adminToHumanModerationQueue(1, 'r'), '进入人工审核失败'],
    ];

    for (const [, call, fallback] of cases) {
      replyOnce({ ok: false, status: 500, json: { message: 1 } });
      await expect(call()).rejects.toThrow(fallback);
    }
  });

  it('adminSetModerationQueueRiskTags falls back riskTags to []', async () => {
    const { adminSetModerationQueueRiskTags } = await import('./moderationQueueService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { id: 1 } });

    await expect((adminSetModerationQueueRiskTags as any)(1, undefined)).resolves.toMatchObject({ id: 1 });

    const info = getFetchCallInfo(lastCall());
    const body = JSON.parse(String(info?.body || '{}'));
    expect(body?.riskTags).toEqual([]);
  });

  it('adminBackfillModerationQueue falls back body to {}', async () => {
    const { adminBackfillModerationQueue } = await import('./moderationQueueService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { scannedPosts: 0, scannedComments: 0, alreadyQueued: 0, enqueued: 0, skipped: 0 } });

    await expect((adminBackfillModerationQueue as any)(undefined)).resolves.toMatchObject({ skipped: 0 });

    const info = getFetchCallInfo(lastCall());
    expect(info?.body).toBe(JSON.stringify({}));
  });
});
