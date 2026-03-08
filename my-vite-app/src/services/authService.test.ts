import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import { mockFetch, mockFetchJsonOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('authService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.resetModules();
  });

  afterEach(() => {
    cleanup();
  });

  it('login throws enriched error when backend provides fields', async () => {
    const { login } = await import('./authService');
    mockFetchJsonOnce({
      ok: false,
      json: { message: '需要二次验证', code: 'LOGIN_2FA_REQUIRED', email: 'a@b.com', methods: ['email', 'totp'] },
    });
    await expect(login('a@b.com', 'p', 'csrf-from-form')).rejects.toMatchObject({
      message: '需要二次验证',
      code: 'LOGIN_2FA_REQUIRED',
      email: 'a@b.com',
      methods: ['email', 'totp'],
    });
  });

  it('login sends credentials and headers and json body', async () => {
    const { login } = await import('./authService');
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { id: 1, email: 'a@b.com', username: 'u', isDeleted: false } });

    await expect(login('a@b.com', 'p', 'csrf-from-form')).resolves.toMatchObject({ id: 1 });

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/login');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-from-form' },
      body: JSON.stringify({ email: 'a@b.com', password: 'p' }),
    });
  });

  it('login filters non-string methods and ignores non-number meta fields', async () => {
    const { login } = await import('./authService');
    mockFetchJsonOnce({
      ok: false,
      json: { message: 'm', methods: ['email', 1, null, 'totp'], resendWaitSeconds: 'bad', codeTtlSeconds: 2, totpDigits: 6 },
    });

    await expect(login('a@b.com', 'p', 'csrf-from-form')).rejects.toMatchObject({
      message: 'm',
      methods: ['email', 'totp'],
      codeTtlSeconds: 2,
      totpDigits: 6,
    });
  });

  it('login falls back to default message when backend message is not string', async () => {
    const { login } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: { message: 1 } });
    await expect(login('a@b.com', 'p', 'csrf-from-form')).rejects.toThrow('登录失败');
  });

  it('login clears csrf token on success', async () => {
    const { login } = await import('./authService');
    const csrf = await import('../utils/csrfUtils');
    mockFetchJsonOnce({ ok: true, json: { id: 1, email: 'a@b.com', username: 'u', isDeleted: false } });
    const res = await login('a@b.com', 'p', 'csrf-from-form');
    expect(res.id).toBe(1);
    expect((csrf as any).clearCsrfToken).toHaveBeenCalledTimes(1);
  });

  it('resendLogin2faEmail throws backend message', async () => {
    const { resendLogin2faEmail } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: { message: '发不了' } });
    await expect(resendLogin2faEmail()).rejects.toThrow('发不了');
  });

  it('verifyLogin2fa clears csrf token on success', async () => {
    const { verifyLogin2fa } = await import('./authService');
    const csrf = await import('../utils/csrfUtils');
    mockFetchJsonOnce({ ok: true, json: { id: 1, email: 'a@b.com', username: 'u', isDeleted: false } });
    const res = await verifyLogin2fa('totp', '000000');
    expect(res.id).toBe(1);
    expect((csrf as any).clearCsrfToken).toHaveBeenCalledTimes(1);
  });

  it('verifyLogin2fa throws fallback message and sends csrf header and credentials', async () => {
    const { verifyLogin2fa } = await import('./authService');
    const fetchMock = mockFetchResponseOnce({ ok: false, status: 400, json: {} });

    await expect(verifyLogin2fa('email', '000000')).rejects.toThrow('验证失败');

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/login/2fa/verify');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
      body: JSON.stringify({ method: 'email', code: '000000' }),
    });
  });

  it('logout clears csrf token even when backend fails', async () => {
    const { logout } = await import('./authService');
    const csrf = await import('../utils/csrfUtils');
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: false,
      json: async () => ({ message: '退出失败' }),
    });
    await expect(logout()).rejects.toThrow('退出失败');
    expect((csrf as any).clearCsrfToken).toHaveBeenCalledTimes(1);
  });

  it('logout rejects when error response json parsing fails and still clears csrf token', async () => {
    const { logout } = await import('./authService');
    const csrf = await import('../utils/csrfUtils');
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: false,
      json: async () => {
        throw new Error('boom');
      },
    });
    await expect(logout()).rejects.toThrow('boom');
    expect((csrf as any).clearCsrfToken).toHaveBeenCalledTimes(1);
  });

  it('logout sends csrf header and credentials', async () => {
    const { logout } = await import('./authService');
    const fetchMock = mockFetchResponseOnce({ ok: false, status: 400, json: { message: '退出失败' } });
    await expect(logout()).rejects.toThrow('退出失败');
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/logout');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'csrf' },
    });
  });

  it('getCurrentAdmin throws on not ok', async () => {
    const { getCurrentAdmin } = await import('./authService');
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({ ok: false, json: async () => ({}) });
    await expect(getCurrentAdmin()).rejects.toThrow('获取当前登录管理员信息失败');
  });

  it('getRegistrationStatus defaults to enabled when field missing', async () => {
    const { getRegistrationStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: {} });
    const res = await getRegistrationStatus();
    expect(res.registrationEnabled).toBe(true);
  });

  it('getRegistrationStatus returns false when backend explicitly disables registration', async () => {
    const { getRegistrationStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { registrationEnabled: false } });
    await expect(getRegistrationStatus()).resolves.toEqual({ registrationEnabled: false });
  });

  it('checkInitialSetupStatus returns json on ok', async () => {
    const { checkInitialSetupStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { setupRequired: true } });
    await expect(checkInitialSetupStatus()).resolves.toEqual({ setupRequired: true });
  });

  it('checkInitialSetupStatus throws on not ok', async () => {
    const { checkInitialSetupStatus } = await import('./authService');
    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(checkInitialSetupStatus()).rejects.toThrow('检查系统初始设置状态失败');
  });

  it('registerInitialAdmin returns api response data when wrapped', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchJsonOnce({
      ok: true,
      json: { success: true, message: 'ok', data: { user: { id: 1, email: 'a@b.com', username: 'u', isDeleted: false } } },
    });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toMatchObject({
      user: { id: 1 },
    });
  });

  it('registerInitialAdmin throws when api response success=false', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: false, message: 'bad' } });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('bad');
  });

  it('registerInitialAdmin maps field error priority on http failure', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchJsonOnce({ ok: false, status: 400, json: { username: 'u required' } });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: '' })).rejects.toThrow('u required');
  });

  it('registerInitialAdmin falls back when error json fails', async () => {
    const { registerInitialAdmin } = await import('./authService');
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({ ok: false, status: 400, json: async () => { throw new Error('boom'); } });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('注册初始管理员失败');
  });

  it('register resolves when backend returns plain object', async () => {
    const { register } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: {} });
    await expect(register({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toBeUndefined();
  });

  it('register throws when api response success=false', async () => {
    const { register } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: false, message: 'bad' } });
    await expect(register({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('bad');
  });

  it('registerAndGetStatus returns message and status from api response wrapper', async () => {
    const { registerAndGetStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: true, message: 'm', data: { status: 'PENDING' } } });
    await expect(registerAndGetStatus({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toEqual({ message: 'm', status: 'PENDING' });
  });

  it('verifyRegister throws when api response success=false', async () => {
    const { verifyRegister } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: false, message: 'bad' } });
    await expect(verifyRegister('a@b.com', '123')).rejects.toThrow('bad');
  });

  it('resendRegisterCode returns typed fields on success', async () => {
    const { resendRegisterCode } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 } });
    await expect(resendRegisterCode('a@b.com')).resolves.toEqual({ message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 });
  });


  it('checkInitialSetupStatus sends GET with credentials and returns json', async () => {
    const { checkInitialSetupStatus } = await import('./authService');
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { setupRequired: true } });
    await expect(checkInitialSetupStatus()).resolves.toEqual({ setupRequired: true });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/initial-setup-status');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('checkInitialSetupStatus throws on non-ok', async () => {
    const { checkInitialSetupStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(checkInitialSetupStatus()).rejects.toThrow('检查系统初始设置状态失败');
  });

  it('registerInitialAdmin returns ApiResponse.data on success=true', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchJsonOnce({
      ok: true,
      json: {
        success: true,
        message: 'ok',
        data: { user: { id: 1, email: 'a@b.com', username: 'u', isDeleted: false } },
      },
    });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toMatchObject({
      user: { id: 1 },
    });
  });

  it('registerInitialAdmin throws ApiResponse.message on success=false', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: false, message: 'no' } });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('no');
  });

  it('registerInitialAdmin throws field error mapping when not ok', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: { username: 'bad username' } });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('bad username');
  });

  it('registerInitialAdmin falls back when response json fails', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('boom') });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('注册初始管理员失败');
  });

  it('register throws ApiResponse.message on success=false', async () => {
    const { register } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: false, message: 'no' } });
    await expect(register({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('no');
  });

  it('register resolves on ApiResponse.success=true', async () => {
    const { register } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: true, message: 'ok' } });
    await expect(register({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toBeUndefined();
  });

  it('registerAndGetStatus returns message and status from ApiResponse.data', async () => {
    const { registerAndGetStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: true, message: 'm', data: { status: 'PENDING' } } });
    await expect(registerAndGetStatus({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toEqual({
      message: 'm',
      status: 'PENDING',
    });
  });

  it('registerAndGetStatus throws on ApiResponse.success=false', async () => {
    const { registerAndGetStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: false, message: 'no' } });
    await expect(registerAndGetStatus({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('no');
  });

  it('verifyRegister throws fallback message when response json fails', async () => {
    const { verifyRegister } = await import('./authService');
    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('boom') });
    await expect(verifyRegister('a@b.com', '0000')).rejects.toThrow('激活失败');
  });

  it('verifyRegister throws on ApiResponse.success=false', async () => {
    const { verifyRegister } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: false, message: 'no' } });
    await expect(verifyRegister('a@b.com', '0000')).rejects.toThrow('no');
  });

  it('resendRegisterCode returns typed fields on success', async () => {
    const { resendRegisterCode } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 } });
    await expect(resendRegisterCode('a@b.com')).resolves.toEqual({ message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 });
  });

  it('resendRegisterCode throws fallback when backend message missing', async () => {
    const { resendRegisterCode } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(resendRegisterCode('a@b.com')).rejects.toThrow('发送失败');
  });

  it('resendLogin2faEmail returns typed fields on success', async () => {
    const { resendLogin2faEmail } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 3, codeTtlSeconds: 4 } });
    await expect(resendLogin2faEmail()).resolves.toEqual({ message: 'ok', resendWaitSeconds: 3, codeTtlSeconds: 4 });
  });

  it('login falls back to default message when backend message missing', async () => {
    const { login } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(login('a@b.com', 'p', 'csrf-from-form')).rejects.toThrow('登录失败');
  });

  it('login maps resendWaitSeconds when backend returns number', async () => {
    const { login } = await import('./authService');
    mockFetchJsonOnce({
      ok: false,
      json: { message: 'm', resendWaitSeconds: 5, codeTtlSeconds: 2, totpDigits: 6, methods: ['email'] },
    });
    await expect(login('a@b.com', 'p', 'csrf-from-form')).rejects.toMatchObject({
      message: 'm',
      resendWaitSeconds: 5,
      codeTtlSeconds: 2,
      totpDigits: 6,
      methods: ['email'],
    });
  });

  it('resendLogin2faEmail throws fallback when backend message is missing or non-string', async () => {
    const { resendLogin2faEmail } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: { message: 1 } });
    await expect(resendLogin2faEmail()).rejects.toThrow('发送失败');
  });

  it('verifyLogin2fa throws backend message when provided', async () => {
    const { verifyLogin2fa } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(verifyLogin2fa('email', '000000')).rejects.toThrow('bad');
  });

  it('logout throws fallback message when backend message missing', async () => {
    const { logout } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(logout()).rejects.toThrow('退出登录失败');
  });

  it('getCurrentAdmin returns json on ok', async () => {
    const { getCurrentAdmin } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { id: 1, email: 'a@b.com', username: 'u', isDeleted: false } });
    await expect(getCurrentAdmin()).resolves.toMatchObject({ id: 1 });
  });

  it('registerInitialAdmin returns plain json when backend does not wrap ApiResponse', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { user: { id: 1, email: 'a@b.com', username: 'u', isDeleted: false } } });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toMatchObject({ user: { id: 1 } });
  });

  it('registerInitialAdmin returns empty object when ApiResponse.data is missing', async () => {
    const { registerInitialAdmin } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: true, message: 'ok' } });
    await expect(registerInitialAdmin({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toEqual({});
  });

  it('register throws fallback message when http failure has no field message', async () => {
    const { register } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(register({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('注册失败');
  });

  it('getRegistrationStatus throws backend message when not ok', async () => {
    const { getRegistrationStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: { message: 'no' } });
    await expect(getRegistrationStatus()).rejects.toThrow('no');
  });

  it('registerAndGetStatus throws fallback when http failure has no field message', async () => {
    const { registerAndGetStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(registerAndGetStatus({ email: 'a@b.com', password: 'p', username: 'u' })).rejects.toThrow('注册失败');
  });

  it('registerAndGetStatus returns undefined fields when types mismatch in ApiResponse', async () => {
    const { registerAndGetStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: { success: true, message: 1, data: { status: 2 } } });
    await expect(registerAndGetStatus({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toEqual({ message: undefined, status: undefined });
  });

  it('registerAndGetStatus returns empty object when backend does not wrap ApiResponse', async () => {
    const { registerAndGetStatus } = await import('./authService');
    mockFetchJsonOnce({ ok: true, json: {} });
    await expect(registerAndGetStatus({ email: 'a@b.com', password: 'p', username: 'u' })).resolves.toEqual({});
  });

  it('verifyRegister throws backend message on http failure', async () => {
    const { verifyRegister } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(verifyRegister('a@b.com', '0000')).rejects.toThrow('bad');
  });

  it('resendRegisterCode throws backend message on http failure', async () => {
    const { resendRegisterCode } = await import('./authService');
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(resendRegisterCode('a@b.com')).rejects.toThrow('bad');
  });
});
