import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('postFilesAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminListPostFiles covers buildQuery filtering and error branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminListPostFiles } = await import('./postFilesAdminService');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0 } });
    await expect(adminListPostFiles({ page: 0, pageSize: 0, postId: 0, fileAssetId: 0, keyword: '', extractStatus: '' })).resolves.toMatchObject({ content: [] });
    const url1 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url1).toContain('/api/admin/post-files?');
    expect(url1).toContain('page=0');
    expect(url1).toContain('pageSize=0');
    expect(url1).toContain('postId=0');
    expect(url1).toContain('fileAssetId=0');
    expect(url1).not.toContain('keyword=');
    expect(url1).not.toContain('extractStatus=');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListPostFiles()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminListPostFiles()).rejects.toThrow('获取帖子文件解析列表失败');
  });

  it('adminGetPostFileDetail covers ok and error branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminGetPostFileDetail } = await import('./postFilesAdminService');

    replyJsonOnce({ ok: true, json: { attachmentId: 1, postId: 2 } });
    await expect(adminGetPostFileDetail(1)).resolves.toMatchObject({ attachmentId: 1, postId: 2 });
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/admin/post-files/1');

    replyJsonOnce({ ok: false, status: 404, json: { message: 'bad' } });
    await expect(adminGetPostFileDetail(1)).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetPostFileDetail(1)).rejects.toThrow('获取解析详情失败');
  });

  it('adminReextractPostFile covers csrf header and error branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminReextractPostFile } = await import('./postFilesAdminService');

    replyJsonOnce({ ok: true, json: { attachmentId: 1, postId: 2 } });
    await expect(adminReextractPostFile(1)).resolves.toMatchObject({ attachmentId: 1 });
    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('POST');
    expect((info?.headers as any)?.['X-XSRF-TOKEN']).toBe('csrf');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminReextractPostFile(1)).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminReextractPostFile(1)).rejects.toThrow('重新解析失败');

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(adminReextractPostFile(1)).rejects.toThrow('csrf-bad');
  });
});

