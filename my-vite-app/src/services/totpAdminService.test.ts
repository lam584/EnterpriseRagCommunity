import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import { getTotpAdminSettings, queryAdminUserTotpStatus, resetAdminUserTotp, updateTotpAdminSettings } from './totpAdminService';

describe('totpAdminService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('getTotpAdminSettings returns json on ok', async () => {
    mockFetchResponseOnce({
      ok: true,
      json: {
        issuer: 'issuer',
        allowedAlgorithms: ['SHA1'],
        allowedDigits: [6],
        allowedPeriodSeconds: [30],
        maxSkew: 1,
        defaultAlgorithm: 'SHA1',
        defaultDigits: 6,
        defaultPeriodSeconds: 30,
        defaultSkew: 1,
      },
    });

    await expect(getTotpAdminSettings()).resolves.toMatchObject({ issuer: 'issuer' });
  });

  it('getTotpAdminSettings throws backend message and falls back on missing/parse failure', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getTotpAdminSettings()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getTotpAdminSettings()).rejects.toThrow('加载 TOTP 策略失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(getTotpAdminSettings()).rejects.toThrow('加载 TOTP 策略失败');
  });

  it('updateTotpAdminSettings sends csrf header and json body', async () => {
    const dto = {
      issuer: 'issuer',
      allowedAlgorithms: ['SHA1'],
      allowedDigits: [6],
      allowedPeriodSeconds: [30],
      maxSkew: 1,
      defaultAlgorithm: 'SHA1',
      defaultDigits: 6,
      defaultPeriodSeconds: 30,
      defaultSkew: 1,
    };
    const fetchMock = mockFetchResponseOnce({ ok: true, json: dto });

    await expect(updateTotpAdminSettings(dto)).resolves.toMatchObject({ issuer: 'issuer' });
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/admin/settings/totp');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
      body: JSON.stringify(dto),
    });
  });

  it('updateTotpAdminSettings throws backend message and falls back when message missing/parse fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(updateTotpAdminSettings({} as any)).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(updateTotpAdminSettings({} as any)).rejects.toThrow('保存 TOTP 策略失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(updateTotpAdminSettings({} as any)).rejects.toThrow('保存 TOTP 策略失败');
  });

  it('queryAdminUserTotpStatus posts csrf header and returns page on ok', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [] } });

    await expect(queryAdminUserTotpStatus({} as any)).resolves.toMatchObject({ content: [] });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/admin/users/totp/query');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
    });
  });

  it('queryAdminUserTotpStatus throws backend message and falls back on parse failure', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(queryAdminUserTotpStatus({} as any)).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(queryAdminUserTotpStatus({} as any)).rejects.toThrow('加载用户 TOTP 状态失败');
  });

  it('resetAdminUserTotp posts csrf header and throws backend message / fallback', async () => {
    const fetchMock1 = mockFetchResponseOnce({ ok: true, json: {} });
    await expect(resetAdminUserTotp(1)).resolves.toBeUndefined();
    expect(fetchMock1.mock.calls[0]?.[0]).toBe('/api/admin/users/totp/1/reset');
    expect(fetchMock1.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        'X-XSRF-TOKEN': 'csrf-token',
      },
    });

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(resetAdminUserTotp(1)).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(resetAdminUserTotp(1)).rejects.toThrow('重置用户 TOTP 失败');
  });
});

