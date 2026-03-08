import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('langLabelAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminGetPostLangLabelGenConfig covers ok and error branches', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();
    const { adminGetPostLangLabelGenConfig } = await import('./langLabelAdminService');

    replyJsonOnce({ ok: true, json: { enabled: true, promptCode: 'p', maxContentChars: 1 } });
    await expect(adminGetPostLangLabelGenConfig()).resolves.toMatchObject({ enabled: true, promptCode: 'p' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetPostLangLabelGenConfig()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetPostLangLabelGenConfig()).rejects.toThrow('获取语言标签生成配置失败');
  });

  it('adminUpsertPostLangLabelGenConfig covers ok, errors and csrf failure', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminUpsertPostLangLabelGenConfig } = await import('./langLabelAdminService');

    replyJsonOnce({ ok: true, json: { enabled: true, promptCode: 'p', maxContentChars: 1 } });
    await expect(adminUpsertPostLangLabelGenConfig({ enabled: true, promptCode: 'p', maxContentChars: 1 } as any)).resolves.toMatchObject({
      enabled: true,
    });
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpsertPostLangLabelGenConfig({ enabled: true, promptCode: 'p', maxContentChars: 1 } as any)).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpsertPostLangLabelGenConfig({ enabled: true, promptCode: 'p', maxContentChars: 1 } as any)).rejects.toThrow(
      '保存语言标签生成配置失败',
    );

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(adminUpsertPostLangLabelGenConfig({ enabled: true, promptCode: 'p', maxContentChars: 1 } as any)).rejects.toThrow('csrf-bad');
  });
});

