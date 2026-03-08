import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('tagGenAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminGetPostTagGenConfig covers ok and error branches', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();
    const { adminGetPostTagGenConfig } = await import('./tagGenAdminService');

    replyJsonOnce({ ok: true, json: { enabled: true, promptCode: 'p', defaultCount: 1, maxCount: 2, maxContentChars: 3, historyEnabled: true } });
    await expect(adminGetPostTagGenConfig()).resolves.toMatchObject({ enabled: true, promptCode: 'p' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetPostTagGenConfig()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetPostTagGenConfig()).rejects.toThrow('获取主题标签生成配置失败');
  });

  it('adminUpsertPostTagGenConfig covers csrf and error branches', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();
    const { adminUpsertPostTagGenConfig } = await import('./tagGenAdminService');

    replyJsonOnce({ ok: true, json: { enabled: false, promptCode: 'p2', defaultCount: 1, maxCount: 2, maxContentChars: 3, historyEnabled: true } });
    await expect(adminUpsertPostTagGenConfig({ enabled: false, promptCode: 'p2', defaultCount: 1, maxCount: 2, maxContentChars: 3, historyEnabled: true } as any)).resolves.toMatchObject({ promptCode: 'p2' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpsertPostTagGenConfig({ enabled: false, promptCode: 'p2', defaultCount: 1, maxCount: 2, maxContentChars: 3, historyEnabled: true } as any)).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpsertPostTagGenConfig({ enabled: false, promptCode: 'p2', defaultCount: 1, maxCount: 2, maxContentChars: 3, historyEnabled: true } as any)).rejects.toThrow('保存主题标签生成配置失败');

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(adminUpsertPostTagGenConfig({ enabled: false, promptCode: 'p2', defaultCount: 1, maxCount: 2, maxContentChars: 3, historyEnabled: true } as any)).rejects.toThrow('csrf-bad');
  });

  it('adminListPostTagGenHistory covers params defaults and userId branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminListPostTagGenHistory } = await import('./tagGenAdminService');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });
    await expect(adminListPostTagGenHistory()).resolves.toMatchObject({ size: 20 });
    const url1 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url1).toContain('/api/admin/semantic/multi-label/history?');
    expect(url1).toContain('page=0');
    expect(url1).toContain('size=20');
    expect(url1).not.toContain('userId=');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 1, size: 10 } });
    await adminListPostTagGenHistory({ page: 1, size: 10, userId: 9 });
    const url2 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url2).toContain('page=1');
    expect(url2).toContain('size=10');
    expect(url2).toContain('userId=9');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });
    await adminListPostTagGenHistory({ userId: 0 });
    const url3 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url3).not.toContain('userId=');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListPostTagGenHistory()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminListPostTagGenHistory()).rejects.toThrow('获取主题标签生成历史失败');
  });
});

