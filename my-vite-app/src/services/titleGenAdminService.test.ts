import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('titleGenAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('adminGetPostTitleGenConfig covers ok and error fallbacks', async () => {
    const { adminGetPostTitleGenConfig } = await import('./titleGenAdminService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: { enabled: true, promptCode: 'p', maxContentChars: 1, defaultCount: 1, maxCount: 1, historyEnabled: false },
    });
    await expect(adminGetPostTitleGenConfig()).resolves.toMatchObject({ promptCode: 'p' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetPostTitleGenConfig()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 500, json: {} });
    await expect(adminGetPostTitleGenConfig()).rejects.toThrow('获取标题生成配置失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad-json') });
    await expect(adminGetPostTitleGenConfig()).rejects.toThrow('获取标题生成配置失败');
  });

  it('adminUpsertPostTitleGenConfig sends csrf header and covers error fallbacks', async () => {
    const { adminUpsertPostTitleGenConfig } = await import('./titleGenAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: { enabled: true, promptCode: 'p', maxContentChars: 1, defaultCount: 1, maxCount: 1, historyEnabled: false },
    });
    await expect(
      adminUpsertPostTitleGenConfig({
        enabled: true,
        promptCode: 'p',
        defaultCount: 1,
        maxCount: 1,
        maxContentChars: 1,
        historyEnabled: false,
      } as any),
    ).resolves.toMatchObject({ enabled: true });
    expect(getFetchCallInfo(lastCall())?.method).toBe('PUT');
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpsertPostTitleGenConfig({ enabled: true } as any)).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 500, json: {} });
    await expect(adminUpsertPostTitleGenConfig({ enabled: true } as any)).rejects.toThrow('保存标题生成配置失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad-json') });
    await expect(adminUpsertPostTitleGenConfig({ enabled: true } as any)).rejects.toThrow('保存标题生成配置失败');
  });

  it('adminListPostTitleGenHistory covers defaults, userId branches, and error fallbacks', async () => {
    const { adminListPostTitleGenHistory } = await import('./titleGenAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });
    await expect(adminListPostTitleGenHistory()).resolves.toMatchObject({ content: [] });
    expect(getFetchCallInfo(lastCall())?.url).toContain('page=0');
    expect(getFetchCallInfo(lastCall())?.url).toContain('size=20');
    expect(getFetchCallInfo(lastCall())?.url).not.toContain('userId=');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 1, size: 5 } });
    await expect(adminListPostTitleGenHistory({ page: 1, size: 5, userId: 9 })).resolves.toMatchObject({ number: 1 });
    expect(getFetchCallInfo(lastCall())?.url).toContain('page=1');
    expect(getFetchCallInfo(lastCall())?.url).toContain('size=5');
    expect(getFetchCallInfo(lastCall())?.url).toContain('userId=9');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 2, size: 20 } });
    await expect(adminListPostTitleGenHistory({ page: 2, userId: 0 })).resolves.toMatchObject({ number: 2 });
    expect(getFetchCallInfo(lastCall())?.url).toContain('page=2');
    expect(getFetchCallInfo(lastCall())?.url).not.toContain('userId=0');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListPostTitleGenHistory({ page: 0, size: 20 })).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad-json') });
    await expect(adminListPostTitleGenHistory({ page: 0, size: 20 })).rejects.toThrow('获取标题生成历史失败');
  });
});

