import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getSecurity2faPolicySettings, updateSecurity2faPolicySettings } from './security2faPolicyAdminService';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

describe('security2faPolicyAdminService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getCsrfTokenMock.mockResolvedValue('csrf');
  });

  it('getSecurity2faPolicySettings sends GET and returns json', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { totpPolicy: 'ALL' } });

    const res = await getSecurity2faPolicySettings();

    expect(res.totpPolicy).toBe('ALL');
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/admin/settings/security-2fa-policy');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getSecurity2faPolicySettings throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getSecurity2faPolicySettings()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getSecurity2faPolicySettings()).rejects.toThrow('加载 2FA 启用策略失败');
  });

  it('getSecurity2faPolicySettings falls back when json parsing throws', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(getSecurity2faPolicySettings()).rejects.toThrow('加载 2FA 启用策略失败');
  });

  it('updateSecurity2faPolicySettings sends PUT with csrf header', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { login2faMode: 'REQUIRED' } });

    const res = await updateSecurity2faPolicySettings({ login2faMode: 'REQUIRED' });

    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/admin/settings/security-2fa-policy');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
      body: JSON.stringify({ login2faMode: 'REQUIRED' }),
    });
    expect(res.login2faMode).toBe('REQUIRED');
  });

  it('updateSecurity2faPolicySettings throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'oops' } });
    await expect(updateSecurity2faPolicySettings({})).rejects.toThrow('oops');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(updateSecurity2faPolicySettings({})).rejects.toThrow('保存 2FA 启用策略失败');
  });

  it('updateSecurity2faPolicySettings falls back when json parsing throws', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(updateSecurity2faPolicySettings({})).rejects.toThrow('保存 2FA 启用策略失败');
  });
});
