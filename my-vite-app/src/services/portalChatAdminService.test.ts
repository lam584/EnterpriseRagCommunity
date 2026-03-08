import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('portalChatAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('adminGetPortalChatConfig covers ok, message/error preference, and json parse fallback', async () => {
    const { adminGetPortalChatConfig } = await import('./portalChatAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { assistantChat: { model: 'm' } } });
    await expect(adminGetPortalChatConfig()).resolves.toMatchObject({ assistantChat: { model: 'm' } });
    expect(parseUrl(getFetchCallInfo(lastCall())!.url).pathname).toBe('/api/admin/ai/portal-chat/config');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminGetPortalChatConfig()).rejects.toThrow('m1');

    replyJsonOnce({ ok: false, status: 400, json: { error: 'e1' } });
    await expect(adminGetPortalChatConfig()).rejects.toThrow('e1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetPortalChatConfig()).rejects.toThrow('获取前台对话配置失败');

    replyOnce({ ok: true, jsonError: new Error('bad') });
    await expect(adminGetPortalChatConfig()).resolves.toEqual({});
  });

  it('adminUpsertPortalChatConfig covers ok and fallback error on json parse failure', async () => {
    const { adminUpsertPortalChatConfig } = await import('./portalChatAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { assistantChat: { model: 'm' } } });
    await expect(adminUpsertPortalChatConfig({ assistantChat: { model: 'm' } })).resolves.toMatchObject({ assistantChat: { model: 'm' } });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('PUT');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpsertPortalChatConfig({})).rejects.toThrow('保存前台对话配置失败');
  });
});

