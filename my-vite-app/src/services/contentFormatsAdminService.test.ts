import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('contentFormatsAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminGetContentFormatsConfig covers ok and error branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminGetContentFormatsConfig } = await import('./contentFormatsAdminService');

    replyJsonOnce({ ok: true, json: { enabled: true } });
    await expect(adminGetContentFormatsConfig()).resolves.toMatchObject({ enabled: true });
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/admin/content/formats/config');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetContentFormatsConfig()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 500, json: { x: 1 } });
    await expect(adminGetContentFormatsConfig()).rejects.toThrow('加载格式配置失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetContentFormatsConfig()).rejects.toThrow('加载格式配置失败');
  });

  it('adminUpdateContentFormatsConfig covers payload default, ok, and error branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminUpdateContentFormatsConfig } = await import('./contentFormatsAdminService');

    replyJsonOnce({ ok: true, json: { enabled: false } });
    await expect(adminUpdateContentFormatsConfig(undefined as any)).resolves.toMatchObject({ enabled: false });
    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('PUT');
    expect((info?.headers as any)?.['X-XSRF-TOKEN']).toBe('csrf');
    expect(JSON.parse(String(info?.body))).toEqual({});

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpdateContentFormatsConfig({ enabled: true } as any)).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpdateContentFormatsConfig({ enabled: true } as any)).rejects.toThrow('保存格式配置失败');

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(adminUpdateContentFormatsConfig({ enabled: true } as any)).rejects.toThrow('csrf-bad');
  });
});

