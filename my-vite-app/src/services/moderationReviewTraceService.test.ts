import { beforeEach, describe, expect, it } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('moderationReviewTraceService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminListModerationReviewTraceTasks builds query with defaults and filters empty values', async () => {
    const { adminListModerationReviewTraceTasks } = await import('./moderationReviewTraceService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 1, totalElements: 0, page: 1, pageSize: 20 } });
    await expect(adminListModerationReviewTraceTasks()).resolves.toMatchObject({ totalElements: 0 });
    const u1 = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u1.pathname).toBe('/api/admin/moderation/review-trace/tasks');
    expect(u1.searchParams.get('page')).toBe('1');
    expect(u1.searchParams.get('pageSize')).toBe('20');

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 1, totalElements: 0, page: 2, pageSize: 10 } });
    await adminListModerationReviewTraceTasks({
      page: 2,
      pageSize: 10,
      traceId: 't',
      status: '',
      updatedFrom: '',
      updatedTo: null as any,
    });
    const u2 = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u2.searchParams.get('page')).toBe('2');
    expect(u2.searchParams.get('pageSize')).toBe('10');
    expect(u2.searchParams.get('traceId')).toBe('t');
    expect(u2.searchParams.has('status')).toBe(false);
    expect(u2.searchParams.has('updatedFrom')).toBe(false);
    expect(u2.searchParams.has('updatedTo')).toBe(false);
  });

  it('adminListModerationReviewTraceTasks prefers backend message and falls back on json parse failure', async () => {
    const { adminListModerationReviewTraceTasks } = await import('./moderationReviewTraceService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminListModerationReviewTraceTasks()).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminListModerationReviewTraceTasks()).rejects.toThrow('获取审核追溯列表失败');
  });

  it('adminGetModerationReviewTraceTaskDetail covers ok and error fallback', async () => {
    const { adminGetModerationReviewTraceTaskDetail } = await import('./moderationReviewTraceService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { queue: { id: 1 } } });
    await expect(adminGetModerationReviewTraceTaskDetail(1)).resolves.toMatchObject({ queue: { id: 1 } });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('GET');
    expect(parseUrl(info1.url).pathname).toBe('/api/admin/moderation/review-trace/tasks/1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetModerationReviewTraceTaskDetail(2)).rejects.toThrow('获取审核追溯详情失败');
  });
});

