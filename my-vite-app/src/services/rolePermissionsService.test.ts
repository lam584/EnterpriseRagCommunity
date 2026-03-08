import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('rolePermissionsService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('createRoleWithMatrix throws ApiError on failure', async () => {
    const { createRoleWithMatrix } = await import('./rolePermissionsService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad', code: 'X' } });
    await expect(createRoleWithMatrix([{ permissionId: 1, allow: true }])).rejects.toMatchObject({ name: 'ApiError', status: 400, code: 'X', message: 'bad' });
  });

  it('createRoleWithMatrix omits X-Admin-Reason when not provided', async () => {
    const { createRoleWithMatrix } = await import('./rolePermissionsService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: [] });
    await expect(createRoleWithMatrix([{ permissionId: 1, allow: true }])).resolves.toEqual([]);

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toBe('/api/admin/role-permissions/role');
    expect(info?.method).toBe('POST');
    expect(info?.init?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(info?.init?.headers).not.toMatchObject({ 'X-Admin-Reason': expect.anything() });
  });

  it('replaceRolePermissions supports empty list (clear matrix) and encodes roleId', async () => {
    const { replaceRolePermissions } = await import('./rolePermissionsService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: [] });
    await expect(replaceRolePermissions(10, [])).resolves.toEqual([]);

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toBe('/api/admin/role-permissions/role/10');
    expect(info?.method).toBe('PUT');
    expect(info?.body).toBe(JSON.stringify([]));
  });

  it('replaceRolePermissions includes X-Admin-Reason when provided', async () => {
    const { replaceRolePermissions } = await import('./rolePermissionsService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: [] });
    await expect(replaceRolePermissions(10, [], { adminReason: 'r' })).resolves.toEqual([]);
    const info = getFetchCallInfo(lastCall());
    expect(info?.init?.headers).toMatchObject({ 'X-Admin-Reason': 'r' });
  });

  it('upsertRolePermission and clearRolePermissions include X-Admin-Reason when provided', async () => {
    const { upsertRolePermission, clearRolePermissions } = await import('./rolePermissionsService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { roleId: 1, permissionId: 2, allow: true } });
    await expect(upsertRolePermission({ roleId: 1, permissionId: 2, allow: true }, { adminReason: 'r' })).resolves.toMatchObject({ roleId: 1 });
    expect(getFetchCallInfo(lastCall())?.init?.headers).toMatchObject({ 'X-Admin-Reason': 'r' });

    replyOnce({ ok: true, status: 200 });
    await expect(clearRolePermissions(1, { adminReason: 'r2' })).resolves.toBeUndefined();
    expect(getFetchCallInfo(lastCall())?.init?.headers).toMatchObject({ 'X-Admin-Reason': 'r2' });
  });

  it('deleteRolePermission builds query string and sets csrf header', async () => {
    const { deleteRolePermission } = await import('./rolePermissionsService');
    const { replyOnce, lastCall } = installFetchMock();

    replyOnce({ ok: true, status: 200 });
    await expect(deleteRolePermission(1, 2)).resolves.toBeUndefined();

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toBe('/api/admin/role-permissions?roleId=1&permissionId=2');
    expect(info?.method).toBe('DELETE');
    expect(info?.init?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('deleteRolePermission includes X-Admin-Reason when provided', async () => {
    const { deleteRolePermission } = await import('./rolePermissionsService');
    const { replyOnce, lastCall } = installFetchMock();

    replyOnce({ ok: true, status: 200 });
    await expect(deleteRolePermission(1, 2, { adminReason: 'r' })).resolves.toBeUndefined();
    expect(getFetchCallInfo(lastCall())?.init?.headers).toMatchObject({ 'X-Admin-Reason': 'r' });
  });

  it('toApiError failure path throws ApiError with json message and code', async () => {
    const { listRoleIds } = await import('./rolePermissionsService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad', code: 'X' } });
    await expect(listRoleIds()).rejects.toMatchObject({ name: 'ApiError', status: 400, code: 'X', message: 'bad' });
  });

  it('toApiError failure path uses text payload when not json', async () => {
    const { listRoleSummaries } = await import('./rolePermissionsService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 500, headers: { 'content-type': 'text/plain; charset=utf-8' }, text: 'bad' });
    await expect(listRoleSummaries()).rejects.toMatchObject({ name: 'ApiError', status: 500, message: 'bad' });
  });
});
