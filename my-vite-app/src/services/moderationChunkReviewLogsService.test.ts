import { beforeEach, describe, expect, it } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

describe('moderationChunkReviewLogsService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminListModerationChunkLogs builds query and returns json', async () => {
    const { adminListModerationChunkLogs } = await import('./moderationChunkReviewLogsService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: [] });
    await expect(
      adminListModerationChunkLogs({ limit: 10, queueId: 1, fileAssetId: 0, status: '' as any, keyword: '' as any }),
    ).resolves.toEqual([]);

    const info = getFetchCallInfo(lastCall())!;
    expect(info.method).toBe('GET');
    expect(info.url).toContain('/api/admin/moderation/chunk-review/logs?');
    expect(info.url).toContain('limit=10');
    expect(info.url).toContain('queueId=1');
    expect(info.url).toContain('fileAssetId=0');
    expect(info.url).not.toContain('status=');
    expect(info.url).not.toContain('keyword=');
  });

  it('adminListModerationChunkLogs throws backend message and json fallback', async () => {
    const { adminListModerationChunkLogs } = await import('./moderationChunkReviewLogsService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminListModerationChunkLogs({ limit: 1 })).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminListModerationChunkLogs({ limit: 1 })).rejects.toThrow('加载最近分片结果失败');
  });

  it('adminGetModerationChunkLogDetail covers ok/error branches', async () => {
    const { adminGetModerationChunkLogDetail } = await import('./moderationChunkReviewLogsService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: { chunk: { id: 1, chunkSetId: 2, queueId: 3 }, chunkSet: { id: 2, queueId: 3 } },
    });
    await expect(adminGetModerationChunkLogDetail(1)).resolves.toMatchObject({ chunk: { id: 1 }, chunkSet: { id: 2 } });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm2' } });
    await expect(adminGetModerationChunkLogDetail(1)).rejects.toThrow('m2');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetModerationChunkLogDetail(1)).rejects.toThrow('加载分片详情失败');
  });

  it('adminGetModerationChunkLogContent forwards signal and covers error branches', async () => {
    const { adminGetModerationChunkLogContent } = await import('./moderationChunkReviewLogsService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    const controller = new AbortController();
    replyJsonOnce({ ok: true, json: { text: 't' } });
    await expect(adminGetModerationChunkLogContent(1, controller.signal)).resolves.toMatchObject({ text: 't' });
    const init1 = getFetchCallInfo(lastCall())!.init as RequestInit;
    expect(init1.signal).toBe(controller.signal);

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm3' } });
    await expect(adminGetModerationChunkLogContent(1)).rejects.toThrow('m3');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetModerationChunkLogContent(1)).rejects.toThrow('加载分片内容失败');
  });
});

