import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { getRegistrationSettings, updateRegistrationSettings } from './adminSettingsService';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

describe('adminSettingsService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf');
  });

  it('getRegistrationSettings sends GET and returns dto', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { defaultRegisterRoleId: 1, registrationEnabled: true } });
    await expect(getRegistrationSettings()).resolves.toMatchObject({ defaultRegisterRoleId: 1 });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/admin/settings/registration');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('getRegistrationSettings throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getRegistrationSettings()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(getRegistrationSettings()).rejects.toThrow('Failed to load registration settings');
  });

  it('updateRegistrationSettings sends PUT with csrf header and body', async () => {
    const dto = { defaultRegisterRoleId: 2, registrationEnabled: false };
    const fetchMock = mockFetchResponseOnce({ ok: true, json: dto });
    await expect(updateRegistrationSettings(dto)).resolves.toMatchObject({ defaultRegisterRoleId: 2 });
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
      body: JSON.stringify(dto),
    });
  });

  it('updateRegistrationSettings throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'oops' } });
    await expect(updateRegistrationSettings({ defaultRegisterRoleId: 1, registrationEnabled: true })).rejects.toThrow('oops');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(updateRegistrationSettings({ defaultRegisterRoleId: 1, registrationEnabled: true })).rejects.toThrow(
      'Failed to update registration settings',
    );
  });
});

