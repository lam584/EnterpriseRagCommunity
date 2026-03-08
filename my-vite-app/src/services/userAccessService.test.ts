import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { ApiError } from './apiError';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import { userAccessService } from './userAccessService';

describe('userAccessService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('queryUsers sends POST with csrf header and json body', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: { content: [], totalPages: 1, totalElements: 0, size: 20, number: 0 },
    });

    const res = await userAccessService.queryUsers({ keyword: 'k' } as any);

    expect(res.totalPages).toBe(1);
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe('/api/users/query');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
      body: JSON.stringify({ keyword: 'k' }),
    });
  });

  it('queryUsers throws on non-ok response', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(userAccessService.queryUsers({} as any)).rejects.toThrow('Failed to query users');
  });

  it('assignRoles validates payload and sends admin reason header', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, status: 204, text: '' });

    await expect(userAccessService.assignRoles(123, [1, 2], { adminReason: 'reason' })).resolves.toBeUndefined();

    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe('/api/users/123/roles');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
        'X-Admin-Reason': 'reason',
      },
      body: JSON.stringify([1, 2]),
    });
  });

  it('assignRoles throws before csrf when roleIds is not an array', async () => {
    await expect(userAccessService.assignRoles(1, 123 as any)).rejects.toThrow('roleIds must be an array');
    expect(getCsrfTokenMock).not.toHaveBeenCalled();
  });

  it('assignRoles throws before csrf when roleIds contains non-number values', async () => {
    await expect(userAccessService.assignRoles(1, [1, '2' as any])).rejects.toThrow('Invalid roleIds payload');
    expect(getCsrfTokenMock).not.toHaveBeenCalled();
  });

  it('assignRoles flattens one-level nested arrays when lengths match', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, status: 204, text: '' });
    await expect(userAccessService.assignRoles(123, [1, [2] as any] as any)).resolves.toBeUndefined();
    const init = fetchMock.mock.calls[0]?.[1] as any;
    expect(init?.body).toBe(JSON.stringify([1, 2]));
  });

  it('assignRoles throws when de-dup shrinks payload length', async () => {
    await expect(userAccessService.assignRoles(1, [1, 1] as any)).rejects.toThrow('Invalid roleIds payload');
    expect(getCsrfTokenMock).not.toHaveBeenCalled();
  });

  it('assignRoles omits admin reason header when not provided', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, status: 204, text: '' });
    await expect(userAccessService.assignRoles(123, [1])).resolves.toBeUndefined();
    const headers = (fetchMock.mock.calls[0]?.[1] as any)?.headers || {};
    expect(headers['X-Admin-Reason']).toBeUndefined();
  });

  it('deleteUser reads backend message from json and throws', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'nope' } });
    await expect(userAccessService.deleteUser(9)).rejects.toThrow('nope');
  });

  it('deleteUser falls back to default message when safeReadErrorMessage returns empty', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, text: '', headers: { 'content-type': 'text/plain' } });
    await expect(userAccessService.deleteUser(9)).rejects.toThrow('Failed to delete user');
  });

  it('hardDeleteUser reads backend text message when not json', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, text: 'nope', headers: { 'content-type': 'text/plain' } });
    await expect(userAccessService.hardDeleteUser(9)).rejects.toThrow('nope');
  });

  it('hardDeleteUser falls back to default message when safeReadErrorMessage returns empty', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, text: '', headers: { 'content-type': 'text/plain' } });
    await expect(userAccessService.hardDeleteUser(9)).rejects.toThrow('Failed to hard delete user');
  });

  it('banUser reads backend text message and throws', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, text: 'nope', headers: { 'content-type': 'text/plain' } });
    await expect(userAccessService.banUser(9, 'r')).rejects.toThrow('nope');
  });

  it('unbanUser falls back to default message when safeReadErrorMessage returns empty', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, text: '', headers: { 'content-type': 'text/plain' } });
    await expect(userAccessService.unbanUser(9, 'r')).rejects.toThrow('解封用户失败');
  });

  it('createUser throws on non-ok response', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(userAccessService.createUser({} as any)).rejects.toThrow('Failed to create user');
  });

  it('createUser resolves on ok', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, json: { id: 1 } });
    await expect(userAccessService.createUser({} as any)).resolves.toMatchObject({ id: 1 });
  });

  it('updateUser throws on non-ok response', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(userAccessService.updateUser({} as any)).rejects.toThrow('Failed to update user');
  });

  it('updateUser resolves on ok', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, json: { id: 1 } });
    await expect(userAccessService.updateUser({} as any)).resolves.toMatchObject({ id: 1 });
  });

  it('getUserRoles throws on non-ok response', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(userAccessService.getUserRoles(1)).rejects.toThrow('Failed to get user roles');
  });

  it('getUserRoles resolves on ok', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, json: [1, 2] });
    await expect(userAccessService.getUserRoles(1)).resolves.toEqual([1, 2]);
  });

  it('getUserById throws on non-ok response', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(userAccessService.getUserById(1)).rejects.toThrow('Failed to get user');
  });

  it('getUserById resolves on ok', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, json: { id: 1 } });
    await expect(userAccessService.getUserById(1)).resolves.toMatchObject({ id: 1 });
  });

  it('banUser falls back to default message when safeReadErrorMessage returns empty', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, text: '', headers: {} as any });
    await expect(userAccessService.banUser(9, 'r')).rejects.toThrow('封禁用户失败');
  });

  it('banUser and unbanUser resolve on ok', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, json: { id: 1 } });
    await expect(userAccessService.banUser(9, 'r')).resolves.toMatchObject({ id: 1 });

    mockFetchResponseOnce({ ok: true, status: 200, json: { id: 1 } });
    await expect(userAccessService.unbanUser(9, 'r')).resolves.toMatchObject({ id: 1 });
  });

  it('safeReadErrorMessage returns json string when body is string', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: 'bad' });
    await expect(userAccessService.deleteUser(9)).rejects.toThrow('bad');
  });

  it('safeReadErrorMessage falls back to stringify when json is object without known keys', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { a: 1 } });
    await expect(userAccessService.deleteUser(9)).rejects.toThrow(JSON.stringify({ a: 1 }));
  });

  it('safeReadErrorMessage prefers error/detail when message missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { error: 'e' } });
    await expect(userAccessService.deleteUser(9)).rejects.toThrow('e');
    mockFetchResponseOnce({ ok: false, status: 400, json: { detail: 'd' } });
    await expect(userAccessService.deleteUser(9)).rejects.toThrow('d');
  });

  it('safeReadErrorMessage returns empty string when json/text throws', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('boom'), headers: { 'content-type': 'application/json' } });
    await expect(userAccessService.deleteUser(9)).rejects.toThrow('Failed to delete user');
  });

  it('safeReadErrorMessage uses empty content-type when header missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, text: '', headers: {} as any });
    await expect(userAccessService.deleteUser(9)).rejects.toThrow('Failed to delete user');
  });

  it('assignRoles converts backend error into ApiError', async () => {
    mockFetchResponseOnce({ ok: false, status: 403, json: { message: 'bad', code: 'X' } });
    const e = await userAccessService.assignRoles(1, [1]).catch((err) => err);
    expect(e).toBeInstanceOf(ApiError);
    expect(e).toMatchObject({ message: 'bad', status: 403, code: 'X' });
  });
});
