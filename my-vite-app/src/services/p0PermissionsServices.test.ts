import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('p0PermissionsServices', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('permissionsService queryPermissions filters empty params and throws ApiError on failure', async () => {
    const { queryPermissions } = await import('./permissionsService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: false, status: 400, headers: { 'content-type': 'application/json' }, json: { message: 'bad', code: 'X' } });

    await expect(queryPermissions({ pageNum: 1, pageSize: 20, resource: '', action: undefined, description: null as any })).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      code: 'X',
      message: 'bad',
    });

    const url = String(lastCall()?.[0] || '');
    expect(url).toContain('/api/admin/permissions?');
    expect(url).toContain('pageNum=1');
    expect(url).toContain('pageSize=20');
    expect(url).not.toContain('resource=');
  });

  it('permissionsService queryPermissions keeps 0 values and returns json on success', async () => {
    const { queryPermissions } = await import('./permissionsService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, size: 0, number: 0 } });

    await expect(queryPermissions({ pageNum: 0, pageSize: 0, id: 0, sort: '' })).resolves.toMatchObject({ content: [], number: 0 });
    const url = String(lastCall()?.[0] || '');
    expect(url).toContain('pageNum=0');
    expect(url).toContain('pageSize=0');
    expect(url).toContain('id=0');
    expect(url).not.toContain('sort=');
  });

  it('permissionsService createPermission sets admin reason header when provided', async () => {
    const { createPermission } = await import('./permissionsService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { id: 1, resource: 'r', action: 'a' } });

    await createPermission({ resource: 'r', action: 'a' }, { adminReason: 'reason' });
    expect(lastCall()?.[1]?.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': 'csrf',
      'X-Admin-Reason': 'reason',
    });
  });

  it('permissionsService create/update/delete do not set admin reason when missing', async () => {
    const { createPermission, updatePermission, deletePermission } = await import('./permissionsService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { id: 1, resource: 'r', action: 'a' } });
    await createPermission({ resource: 'r', action: 'a' });
    expect(lastCall()?.[1]?.headers).not.toHaveProperty('X-Admin-Reason');

    replyJsonOnce({ ok: true, json: { id: 1, resource: 'r', action: 'b' } });
    await updatePermission({ id: 1, action: 'b' });
    expect(lastCall()?.[1]?.headers).not.toHaveProperty('X-Admin-Reason');

    replyOnce({ ok: true, status: 200 });
    await deletePermission(1);
    expect(lastCall()?.[1]?.headers).not.toHaveProperty('X-Admin-Reason');
  });

  it('permissionsService create/update/delete throw ApiError on failure', async () => {
    const { createPermission, updatePermission, deletePermission } = await import('./permissionsService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad', code: 'X' } });
    await expect(createPermission({ resource: 'r', action: 'a' })).rejects.toMatchObject({ name: 'ApiError', status: 400, message: 'bad', code: 'X' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad', code: 'X' } });
    await expect(updatePermission({ id: 1, action: 'a' })).rejects.toMatchObject({ name: 'ApiError', status: 400, message: 'bad', code: 'X' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad', code: 'X' } });
    await expect(deletePermission(1)).rejects.toMatchObject({ name: 'ApiError', status: 400, message: 'bad', code: 'X' });
  });

  it('rolePermissionsService createRoleWithMatrix sends csrf and admin reason', async () => {
    const { createRoleWithMatrix } = await import('./rolePermissionsService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: [] });

    await createRoleWithMatrix([{ permissionId: 1, allow: true }], { adminReason: 'r' });
    expect(lastCall()?.[0]).toBe('/api/admin/role-permissions/role');
    expect(lastCall()?.[1]?.method).toBe('POST');
    expect(lastCall()?.[1]?.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': 'csrf',
      'X-Admin-Reason': 'r',
    });
  });

  it('UserRoleService createReaderPermission throws backend message and logs error', async () => {
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const { createReaderPermission } = await import('./UserRoleService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'nope' } });
    await expect(createReaderPermission({ roles: 'x' })).rejects.toThrow('nope');
    expect(errSpy).toHaveBeenCalled();
  });

  it('UserRoleService createReaderPermission falls back when response json fails', async () => {
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const { createReaderPermission } = await import('./UserRoleService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(createReaderPermission({ roles: 'x' })).rejects.toThrow('创建读者权限失败');
    expect(errSpy).toHaveBeenCalled();
  });

  it('UserRoleService createReaderPermission covers json-parse-failure branch and csrf failure', async () => {
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const { createReaderPermission } = await import('./UserRoleService');
    const csrf = await import('../utils/csrfUtils');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(createReaderPermission({ roles: 'x' })).rejects.toThrow('创建读者权限失败');
    expect(errSpy).toHaveBeenCalled();

    (csrf as any).getCsrfToken.mockRejectedValueOnce(new Error('csrf bad'));
    await expect(createReaderPermission({ roles: 'x' })).rejects.toThrow('csrf bad');
    expect(errSpy).toHaveBeenCalled();
  });

  it('UserRoleService fetchReaderPermissions and fetchReaderPermissionById throw on failure', async () => {
    const { fetchReaderPermissions, fetchReaderPermissionById } = await import('./UserRoleService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 500, text: 'bad' });
    await expect(fetchReaderPermissions()).rejects.toThrow('获取读者权限列表失败');

    replyOnce({ ok: false, status: 500, text: 'bad' });
    await expect(fetchReaderPermissionById(1)).rejects.toThrow('获取读者权限详情失败');
  });

  it('UserRoleService createReaderPermission falls back when backend message missing', async () => {
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const { createReaderPermission } = await import('./UserRoleService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(createReaderPermission({ roles: 'x' })).rejects.toThrow('创建读者权限失败');
    expect(errSpy).toHaveBeenCalled();
  });

  it('permissionsService get/update/delete cover happy paths and ApiError failures', async () => {
    const { getPermissionById, updatePermission, deletePermission } = await import('./permissionsService');
    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { id: 1, resource: 'r', action: 'a' } });
    await expect(getPermissionById(1)).resolves.toMatchObject({ id: 1, resource: 'r', action: 'a' });
    expect(lastCall()?.[0]).toBe('/api/admin/permissions/1');
    expect(lastCall()?.[1]?.method).toBe('GET');

    replyOnce({ ok: false, status: 500, headers: { 'content-type': 'text/plain; charset=utf-8' }, text: 'bad' });
    await expect(getPermissionById(1)).rejects.toMatchObject({ name: 'ApiError', status: 500, message: 'bad' });

    replyJsonOnce({ ok: true, json: { id: 1, resource: 'r', action: 'b' } });
    await expect(updatePermission({ id: 1, action: 'b' }, { adminReason: 'reason' })).resolves.toMatchObject({ action: 'b' });
    expect(lastCall()?.[1]?.method).toBe('PUT');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf', 'X-Admin-Reason': 'reason' });

    replyJsonOnce({ ok: true, json: {} });
    await expect(deletePermission(1, { adminReason: 'r' })).resolves.toBeUndefined();
    expect(lastCall()?.[1]?.method).toBe('DELETE');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf', 'X-Admin-Reason': 'r' });
  });

  it('UserRoleService covers fetch/update/delete happy paths', async () => {
    const {
      fetchReaderPermissions,
      fetchReaderPermissionById,
      createReaderPermission,
      updateReaderPermission,
      deleteReaderPermission,
    } = await import('./UserRoleService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: [{ id: 1, roles: 'x' }] });
    await expect(fetchReaderPermissions()).resolves.toMatchObject([{ id: 1 }]);
    expect(lastCall()?.[0]).toBe('/api/reader-permissions');

    replyJsonOnce({ ok: true, json: { id: 2, roles: 'y' } });
    await expect(fetchReaderPermissionById(2)).resolves.toMatchObject({ id: 2 });
    expect(lastCall()?.[0]).toBe('/api/reader-permissions/2');

    replyJsonOnce({ ok: true, json: { id: 3, roles: 'z' } });
    await expect(createReaderPermission({ roles: 'z' })).resolves.toMatchObject({ id: 3 });
    expect(lastCall()?.[1]?.method).toBe('POST');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: true, json: { id: 3, roles: 'z' } });
    await expect(updateReaderPermission(3, { id: 3, roles: 'z' })).resolves.toMatchObject({ id: 3 });
    expect(lastCall()?.[1]?.method).toBe('PUT');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyOnce({ ok: true, status: 200 });
    await expect(deleteReaderPermission(3)).resolves.toBeUndefined();
    expect(lastCall()?.[1]?.method).toBe('DELETE');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('UserRoleService update/delete throw fallback errors on failure', async () => {
    const { updateReaderPermission, deleteReaderPermission } = await import('./UserRoleService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(updateReaderPermission(1, { id: 1 } as any)).rejects.toThrow('更新读者权限失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(deleteReaderPermission(1)).rejects.toThrow('删除读者权限失败');
  });

  it('UserRoleService update/delete cover failure branches', async () => {
    const { updateReaderPermission, deleteReaderPermission } = await import('./UserRoleService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(updateReaderPermission(1, { id: 1, roles: 'x' } as any)).rejects.toThrow('更新读者权限失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(deleteReaderPermission(1)).rejects.toThrow('删除读者权限失败');
  });

  it('rolesService placeholder throws explicit error', async () => {
    const { rolesService } = await import('./rolesService');
    await expect(rolesService.getAllRoles()).rejects.toThrow('user_roles 已删除：不再提供角色列表接口');
  });
});
