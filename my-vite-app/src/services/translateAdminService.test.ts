import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('translateAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminGetTranslateConfig covers ok and error branches', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();
    const { adminGetTranslateConfig } = await import('./translateAdminService');

    replyJsonOnce({ ok: true, json: { enabled: true, promptCode: 'p', maxContentChars: 1, historyEnabled: true } });
    await expect(adminGetTranslateConfig()).resolves.toMatchObject({ enabled: true, promptCode: 'p' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetTranslateConfig()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetTranslateConfig()).rejects.toThrow('获取翻译配置失败');
  });

  it('adminUpsertTranslateConfig covers csrf and error branches', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();
    const { adminUpsertTranslateConfig } = await import('./translateAdminService');

    replyJsonOnce({ ok: true, json: { enabled: false, promptCode: 'p2', maxContentChars: 1, historyEnabled: true } });
    await expect(adminUpsertTranslateConfig({ enabled: false, promptCode: 'p2', maxContentChars: 1, historyEnabled: true } as any)).resolves.toMatchObject({ promptCode: 'p2' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpsertTranslateConfig({ enabled: false, promptCode: 'p2', maxContentChars: 1, historyEnabled: true } as any)).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpsertTranslateConfig({ enabled: false, promptCode: 'p2', maxContentChars: 1, historyEnabled: true } as any)).rejects.toThrow('保存翻译配置失败');

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(adminUpsertTranslateConfig({ enabled: false, promptCode: 'p2', maxContentChars: 1, historyEnabled: true } as any)).rejects.toThrow('csrf-bad');
  });

  it('adminListTranslateHistory covers params defaults and userId branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminListTranslateHistory } = await import('./translateAdminService');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });
    await expect(adminListTranslateHistory()).resolves.toMatchObject({ size: 20 });
    const url1 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url1).toContain('/api/admin/semantic/translate/history?');
    expect(url1).toContain('page=0');
    expect(url1).toContain('size=20');
    expect(url1).not.toContain('userId=');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 1, size: 10 } });
    await adminListTranslateHistory({ page: 1, size: 10, userId: 9 });
    const url2 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url2).toContain('page=1');
    expect(url2).toContain('size=10');
    expect(url2).toContain('userId=9');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });
    await adminListTranslateHistory({ userId: 0 });
    const url3 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url3).not.toContain('userId=');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListTranslateHistory()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminListTranslateHistory()).rejects.toThrow('获取翻译历史失败');
  });
});

