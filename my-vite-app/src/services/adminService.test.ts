import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('adminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('fetchAdministrators returns json on ok and throws on not ok', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const { fetchAdministrators } = await import('./adminService');

    replyJsonOnce({ ok: true, json: [{ id: 1, account: 'a' }] });
    await expect(fetchAdministrators()).resolves.toMatchObject([{ id: 1 }]);
    expect(getFetchCallInfo(lastCall())?.url).toContain('/administrators');
    expect(getFetchCallInfo(lastCall())?.init?.credentials).toBe('include');

    replyJsonOnce({ ok: false, status: 500, json: { message: 'x' } });
    await expect(fetchAdministrators()).rejects.toThrow('获取管理员列表失败');
  });

  it('searchAdministrators builds query params branches', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const { searchAdministrators } = await import('./adminService');

    replyJsonOnce({ ok: true, json: [] });
    await searchAdministrators({ id: 0, account: 'acc', phone: 'p', email: 'e@x.com' });
    const url1 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url1).toContain('/administrators/search?');
    expect(url1).toContain('id=0');
    expect(url1).toContain('account=acc');
    expect(url1).toContain('phone=p');
    expect(url1).toContain('email=e%40x.com');

    replyJsonOnce({ ok: true, json: [] });
    await searchAdministrators({ id: undefined, account: '', phone: undefined, email: undefined });
    const url2 = getFetchCallInfo(lastCall())?.url ?? '';
    expect(url2).toContain('/administrators/search?');
    expect(url2).not.toContain('id=');
    expect(url2).not.toContain('account=');
    expect(url2).not.toContain('phone=');
    expect(url2).not.toContain('email=');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'x' } });
    await expect(searchAdministrators({ account: 'acc' })).rejects.toThrow('搜索管理员失败');
  });

  it('fetchAdministratorById returns json on ok and throws on not ok', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const { fetchAdministratorById } = await import('./adminService');

    replyJsonOnce({ ok: true, json: { id: 9, account: 'a' } });
    await expect(fetchAdministratorById(9)).resolves.toMatchObject({ id: 9 });
    expect(getFetchCallInfo(lastCall())?.url).toContain('/administrators/9');

    replyJsonOnce({ ok: false, status: 404, json: { message: 'x' } });
    await expect(fetchAdministratorById(9)).rejects.toThrow('获取管理员 9 详情失败');
  });

  it('updateAdministrator sends csrf header and throws on failures', async () => {
    const { replyOnce, lastCall } = installFetchMock();
    const { updateAdministrator } = await import('./adminService');

    replyOnce({ ok: true, status: 204, text: '' });
    await expect(
      updateAdministrator(1, { phone: 'p', email: 'e', sex: 'M', permissionsId: 2, isActive: true }),
    ).resolves.toBeUndefined();
    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('PUT');
    expect(info?.url).toContain('/administrators/1');
    expect((info?.headers as any)?.['X-XSRF-TOKEN']).toBe('csrf');
    expect((info?.headers as any)?.['Content-Type']).toBe('application/json');
    expect(JSON.parse(String(info?.body))).toMatchObject({ phone: 'p', isActive: true });

    replyOnce({ ok: false, status: 500, text: '' });
    await expect(
      updateAdministrator(2, { phone: 'p', email: 'e', sex: 'M', permissionsId: 2, isActive: true, password: 'x' }),
    ).rejects.toThrow('更新管理员 2 失败');

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(
      updateAdministrator(3, { phone: 'p', email: 'e', sex: 'M', permissionsId: 2, isActive: true }),
    ).rejects.toThrow('csrf-bad');
  });
});

