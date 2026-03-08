import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockConsole } from '../testUtils/mockConsole';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import { changePassword, getAccountInfo, getMyProfile, updateAccountInfo, updateMyProfile } from './accountService';

describe('accountService', () => {
  let consoleMock: ReturnType<typeof mockConsole>;

  beforeEach(() => {
    vi.restoreAllMocks();
    consoleMock = mockConsole();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  afterEach(() => {
    consoleMock.restore();
  });

  it('getAccountInfo sends GET with credentials and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: { id: 1, account: 'a', email: 'e', phone: 'p', sex: 'M' },
    });

    const res = await getAccountInfo();

    expect(res).toEqual({ id: 1, account: 'a', email: 'e', phone: 'p', sex: 'M' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/account/me');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getAccountInfo throws default message when not ok', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(getAccountInfo()).rejects.toThrow('获取账户信息失败');
  });

  it('updateAccountInfo sends PUT with csrf header and body', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, status: 204 });

    await expect(updateAccountInfo({ email: 'x@example.com' })).resolves.toBeUndefined();

    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/account/me');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({ email: 'x@example.com' }),
    });
  });

  it('updateAccountInfo throws backend message when provided', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(updateAccountInfo({})).rejects.toThrow('bad');
  });

  it('updateAccountInfo falls back when response json fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('boom') });
    await expect(updateAccountInfo({})).rejects.toThrow('更新账户信息失败');
  });

  it('changePassword sends POST with csrf header and body', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, status: 204 });

    await expect(changePassword({ currentPassword: 'a', newPassword: 'b', totpCode: '123' })).resolves.toBeUndefined();

    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/account/password');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({ currentPassword: 'a', newPassword: 'b', totpCode: '123' }),
    });
  });

  it('changePassword throws backend message when provided', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(changePassword({ currentPassword: 'a', newPassword: 'b' })).rejects.toThrow('bad');
  });

  it('changePassword falls back when response json fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('boom') });
    await expect(changePassword({ currentPassword: 'a', newPassword: 'b' })).rejects.toThrow('修改密码失败');
  });

  it('getMyProfile maps profilePending first, preserves empty string, and maps profileModeration', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        id: 7,
        email: 'me@example.com',
        username: 'public-user',
        metadata: {
          profile: { avatarUrl: 'public-avatar', bio: 'public-bio' },
          profilePending: { username: 'pending-user', avatarUrl: '', bio: null, location: 'sh', website: undefined },
          profileModeration: { caseType: 'PROFILE', queueId: '12', status: 'PENDING', stage: 'AUTO', updatedAt: 't', reason: '' },
        },
      },
    });

    const p = await getMyProfile();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/account/profile');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
    expect(p.id).toBe(7);
    expect(p.email).toBe('me@example.com');
    expect(p.username).toBe('pending-user');
    expect(p.avatarUrl).toBe('');
    expect(p.bio).toBeUndefined();
    expect(p.location).toBe('sh');
    expect(p.website).toBeUndefined();
    expect(p.publicProfile?.username).toBe('public-user');
    expect(p.publicProfile?.avatarUrl).toBe('public-avatar');
    expect(p.profileModeration).toEqual({
      caseType: 'PROFILE',
      queueId: 12,
      status: 'PENDING',
      stage: 'AUTO',
      updatedAt: 't',
      reason: '',
    });
  });

  it('getMyProfile uses public username when pending username is null', async () => {
    mockFetchResponseOnce({
      ok: true,
      json: {
        id: 1,
        email: 'e',
        username: 'public-user',
        metadata: { profilePending: { username: null }, profile: { avatarUrl: 'a' } },
      },
    });

    const p = await getMyProfile();
    expect(p.username).toBe('public-user');
  });

  it('getMyProfile handles metadata with profilePending missing username field', async () => {
    mockFetchResponseOnce({
      ok: true,
      json: {
        id: 1,
        email: 'e',
        username: 'public-user',
        metadata: { profilePending: { avatarUrl: 'x' } },
      },
    });
    const p = await getMyProfile();
    expect(p.username).toBe('public-user');
    expect(p.avatarUrl).toBe('x');
  });

  it('getMyProfile maps profileModeration even when empty object', async () => {
    mockFetchResponseOnce({
      ok: true,
      json: {
        id: 1,
        email: 'e',
        username: 'u',
        metadata: { profileModeration: {} },
      },
    });
    const p = await getMyProfile();
    expect(p.profileModeration).toEqual({
      caseType: undefined,
      queueId: undefined,
      status: undefined,
      stage: undefined,
      updatedAt: undefined,
      reason: undefined,
    });
  });

  it('getMyProfile tolerates missing metadata and missing username', async () => {
    mockFetchResponseOnce({
      ok: true,
      json: {
        id: 1,
        email: 'e',
      },
    });
    const p = await getMyProfile();
    expect(p.username).toBe('');
    expect(p.avatarUrl).toBeUndefined();
    expect(p.profileModeration).toBeUndefined();
  });

  it('getMyProfile throws backend message from message/error and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'm' } });
    await expect(getMyProfile()).rejects.toThrow('m');

    mockFetchResponseOnce({ ok: false, status: 400, json: { error: 'e' } });
    await expect(getMyProfile()).rejects.toThrow('e');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(getMyProfile()).rejects.toThrow('加载个人资料失败');
  });

  it('getMyProfile falls back when response json fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('boom') });
    await expect(getMyProfile()).rejects.toThrow('加载个人资料失败');
  });

  it('updateMyProfile sends PUT with csrf header and maps response', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        id: 2,
        email: 'me@example.com',
        username: 'public-user',
        metadata: { profilePending: { username: 'p' }, profile: { avatarUrl: 'pub' } },
      },
    });

    const res = await updateMyProfile({ username: 'p', bio: null });

    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/account/profile');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({ username: 'p', bio: null }),
    });
    expect(res.username).toBe('p');
  });

  it('updateMyProfile throws backend message from message/error and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'm' } });
    await expect(updateMyProfile({})).rejects.toThrow('m');

    mockFetchResponseOnce({ ok: false, status: 400, json: { error: 'e' } });
    await expect(updateMyProfile({})).rejects.toThrow('e');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(updateMyProfile({})).rejects.toThrow('更新个人资料失败');
  });

  it('updateMyProfile falls back when response json fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('boom') });
    await expect(updateMyProfile({})).rejects.toThrow('更新个人资料失败');
  });
});
