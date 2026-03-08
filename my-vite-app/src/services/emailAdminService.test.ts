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

describe('emailAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('getEmailAdminSettings covers ok, backend message, and json parse fallback', async () => {
    const { getEmailAdminSettings } = await import('./emailAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { enabled: true } });
    await expect(getEmailAdminSettings()).resolves.toMatchObject({ enabled: true });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('GET');
    expect(parseUrl(info1.url).pathname).toBe('/api/admin/settings/email');
    expect(info1.init).toMatchObject({ credentials: 'include' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(getEmailAdminSettings()).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(getEmailAdminSettings()).rejects.toThrow('加载邮箱配置失败');
  });

  it('updateEmailAdminSettings includes csrf header and covers backend message and fallback', async () => {
    const { updateEmailAdminSettings } = await import('./emailAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { enabled: false } });
    await expect(updateEmailAdminSettings({ enabled: false })).resolves.toMatchObject({ enabled: false });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('PUT');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
    expect(info1.body).toBe(JSON.stringify({ enabled: false }));

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm2' } });
    await expect(updateEmailAdminSettings({ enabled: true })).rejects.toThrow('m2');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(updateEmailAdminSettings({ enabled: true })).rejects.toThrow('保存邮箱配置失败');
  });

  it('sendEmailAdminTest covers ok and fallback error on json parse failure', async () => {
    const { sendEmailAdminTest } = await import('./emailAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: {} });
    await expect(sendEmailAdminTest('a@b.com')).resolves.toBeUndefined();
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('POST');
    expect(parseUrl(info1.url).pathname).toBe('/api/admin/settings/email/test');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
    expect(info1.body).toBe(JSON.stringify({ to: 'a@b.com' }));

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(sendEmailAdminTest('a@b.com')).rejects.toThrow('发送测试邮件失败');
  });

  it('email inbox methods cover backend message branches and json parse fallbacks', async () => {
    const { sendEmailAdminTest, getEmailInboxAdminSettings, updateEmailInboxAdminSettings, listEmailInboxMessages } = await import('./emailAdminService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm-send' } });
    await expect(sendEmailAdminTest('a@b.com')).rejects.toThrow('m-send');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(getEmailInboxAdminSettings()).rejects.toThrow('加载收件配置失败');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm-inbox' } });
    await expect(updateEmailInboxAdminSettings({ host: 'h' })).rejects.toThrow('m-inbox');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(listEmailInboxMessages(1)).rejects.toThrow('加载收件箱失败');
  });

  it('inbox settings and list methods cover limit default/override and fallback messages', async () => {
    const {
      getEmailInboxAdminSettings,
      updateEmailInboxAdminSettings,
      listEmailInboxMessages,
      listEmailSentMessages,
    } = await import('./emailAdminService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { host: 'h' } });
    await expect(getEmailInboxAdminSettings()).resolves.toMatchObject({ host: 'h' });
    expect(parseUrl(getFetchCallInfo(lastCall())!.url).pathname).toBe('/api/admin/settings/email/inbox-config');

    replyJsonOnce({ ok: true, json: { host: 'h2' } });
    await expect(updateEmailInboxAdminSettings({ host: 'h2' })).resolves.toMatchObject({ host: 'h2' });
    expect(getFetchCallInfo(lastCall())!.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-token' });

    replyJsonOnce({ ok: true, json: [] });
    await expect(listEmailInboxMessages()).resolves.toEqual([]);
    const u1 = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u1.pathname).toBe('/api/admin/settings/email/inbox');
    expect(u1.searchParams.get('limit')).toBe('20');

    replyJsonOnce({ ok: true, json: [] });
    await expect(listEmailSentMessages(5)).resolves.toEqual([]);
    const u2 = parseUrl(getFetchCallInfo(lastCall())!.url);
    expect(u2.pathname).toBe('/api/admin/settings/email/sent');
    expect(u2.searchParams.get('limit')).toBe('5');

    replyJsonOnce({ ok: false, status: 400, json: { message: 123 } });
    await expect(listEmailInboxMessages(1)).rejects.toThrow('加载收件箱失败');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm3' } });
    await expect(getEmailInboxAdminSettings()).rejects.toThrow('m3');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(updateEmailInboxAdminSettings({ host: 'h' })).rejects.toThrow('保存收件配置失败');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm4' } });
    await expect(listEmailSentMessages(1)).rejects.toThrow('m4');
  });
});
