import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('adminPermissionService', () => {
  beforeEach(() => {
    resetServiceTest();
    vi.spyOn(console, 'log').mockImplementation(() => undefined);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('fetchAdminPermissions maps name to roles and sends csrf header', async () => {
    const { fetchAdminPermissions } = await import('./adminPermissionService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: [
        { id: 1, name: 'ROLE_ADMIN', description: 'd', allowEditUserProfile: true, createdAt: 'c', updatedAt: 'u' },
        { id: 2, name: 'ROLE_MOD', description: 'd2', allowEditUserProfile: false, createdAt: 'c2', updatedAt: 'u2' },
      ],
    });

    const res = await fetchAdminPermissions();
    expect(res).toEqual([
      { id: 1, roles: 'ROLE_ADMIN', description: 'd', allowEditUserProfile: true, createdAt: 'c', updatedAt: 'u' },
      { id: 2, roles: 'ROLE_MOD', description: 'd2', allowEditUserProfile: false, createdAt: 'c2', updatedAt: 'u2' },
    ]);

    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('GET');
    expect(info?.url).toBe('/api/admin-permissions');
    expect((info?.init as any)?.credentials).toBe('include');
    expect((info?.init as any)?.headers?.['X-XSRF-TOKEN']).toBe('csrf');
  });

  it('fetchAdminPermissions throws on non-ok response', async () => {
    const { fetchAdminPermissions } = await import('./adminPermissionService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 500, json: {} });
    await expect(fetchAdminPermissions()).rejects.toThrow('HTTP 状态码：500');
  });

  it('createAdminPermission posts json with refreshed csrf token', async () => {
    const csrf = await import('../utils/csrfUtils');
    (csrf as any).getCsrfToken.mockImplementation(async (force?: boolean) => (force ? 'csrf2' : 'csrf'));

    const { createAdminPermission } = await import('./adminPermissionService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: { id: 1, name: 'ROLE_ADMIN', description: 'd', allowEditUserProfile: true, createdAt: 'c', updatedAt: 'u' },
    });

    const res = await createAdminPermission({ name: 'ROLE_ADMIN', description: 'd', allowEditUserProfile: true });
    expect(res.roles).toBe('ROLE_ADMIN');

    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('POST');
    expect(info?.url).toBe('/api/admin-permissions');
    expect((info?.init as any)?.credentials).toBe('include');
    expect((info?.init as any)?.headers?.['X-XSRF-TOKEN']).toBe('csrf2');
    expect((info?.init as any)?.headers?.['Content-Type']).toBe('application/json');
    expect(info?.body).toBe(JSON.stringify({ name: 'ROLE_ADMIN', description: 'd', allowEditUserProfile: true }));
  });

  it('createAdminPermission throws with response text on failure', async () => {
    const { createAdminPermission } = await import('./adminPermissionService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 400, text: 'bad' });
    await expect(createAdminPermission({ name: 'X' })).rejects.toThrow(/HTTP 状态码：400.*bad/);
  });

  it('updateAdminPermission sends PUT and throws on failure', async () => {
    const { updateAdminPermission } = await import('./adminPermissionService');
    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: { id: 2, name: 'ROLE_MOD', description: 'd', allowEditUserProfile: false, createdAt: 'c', updatedAt: 'u' },
    });
    const okRes = await updateAdminPermission(2, { description: 'd' });
    expect(okRes.id).toBe(2);

    const okInfo = getFetchCallInfo(lastCall());
    expect(okInfo?.method).toBe('PUT');
    expect(okInfo?.url).toBe('/api/admin-permissions/2');
    expect(okInfo?.body).toBe(JSON.stringify({ description: 'd' }));

    replyOnce({ ok: false, status: 500, text: 'no' });
    await expect(updateAdminPermission(3, { description: 'x' })).rejects.toThrow('更新管理员权限失败');
  });

  it('deleteAdminPermission sends DELETE and throws on failure', async () => {
    const { deleteAdminPermission } = await import('./adminPermissionService');
    const { replyOnce, lastCall } = installFetchMock();

    replyOnce({ ok: true, status: 204 });
    await deleteAdminPermission(5);
    const okInfo = getFetchCallInfo(lastCall());
    expect(okInfo?.method).toBe('DELETE');
    expect(okInfo?.url).toBe('/api/admin-permissions/5');

    replyOnce({ ok: false, status: 404, text: 'no' });
    await expect(deleteAdminPermission(6)).rejects.toThrow('删除管理员权限失败');
  });
});
