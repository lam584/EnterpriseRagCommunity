import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('p0SecurityServices', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('passwordResetService getPasswordResetStatus parses booleans and sends csrf header', async () => {
    const { getPasswordResetStatus } = await import('./passwordResetService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { allowed: 1, totpEnabled: 0, emailEnabled: true, message: undefined } });

    const res = await getPasswordResetStatus('a@b.com');
    expect(res).toEqual({ allowed: true, totpEnabled: false, emailEnabled: true, message: null });

    const call = lastCall();
    expect(call?.[0]).toBe('/api/auth/password-reset/status');
    expect(call?.[1]?.method).toBe('POST');
    expect(call?.[1]?.credentials).toBe('include');
    expect(call?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(call?.[1]?.body).toBe(JSON.stringify({ email: 'a@b.com' }));
  });

  it('passwordResetService resetPasswordByTotp throws backend message on failure', async () => {
    const { resetPasswordByTotp } = await import('./passwordResetService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(resetPasswordByTotp('a@b.com', '000000', 'p')).rejects.toThrow('bad');
  });

  it('emailChangeService verifyEmailChangePassword throws fallback message when missing', async () => {
    const { verifyEmailChangePassword } = await import('./emailChangeService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(verifyEmailChangePassword('p')).rejects.toThrow('密码验证失败');
  });

  it('security2faPolicyAccountService verifyMyLogin2faPreferencePassword returns message', async () => {
    const { verifyMyLogin2faPreferencePassword } = await import('./security2faPolicyAccountService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: true, json: { message: 'ok' } });
    await expect(verifyMyLogin2faPreferencePassword('p')).resolves.toEqual({ message: 'ok' });
  });

  it('security2faPolicyAccountService verifyMyLogin2faPreferencePassword covers missing message and error paths', async () => {
    const { verifyMyLogin2faPreferencePassword } = await import('./security2faPolicyAccountService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { message: 1 } });
    await expect(verifyMyLogin2faPreferencePassword('p')).resolves.toEqual({ message: undefined });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(verifyMyLogin2faPreferencePassword('p')).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(verifyMyLogin2faPreferencePassword('p')).rejects.toThrow('验证密码失败');
  });

  it('security2faPolicyAdminService updateSecurity2faPolicySettings throws backend message', async () => {
    const { updateSecurity2faPolicySettings } = await import('./security2faPolicyAdminService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { message: 'no' } });
    await expect(updateSecurity2faPolicySettings({ totpPolicy: 'REQUIRED' })).rejects.toThrow('no');
  });

  it('totpAdminService resetAdminUserTotp throws backend message', async () => {
    const { resetAdminUserTotp } = await import('./totpAdminService');
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { message: 'fail' } });
    await expect(resetAdminUserTotp(1)).rejects.toThrow('fail');
  });

  it('totpAdminService get/update/query succeed and send csrf header', async () => {
    const { getTotpAdminSettings, updateTotpAdminSettings, queryAdminUserTotpStatus, resetAdminUserTotp } = await import('./totpAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({
      ok: true,
      json: {
        issuer: 'i',
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
    await expect(getTotpAdminSettings()).resolves.toMatchObject({ issuer: 'i' });
    expect(lastCall()?.[0]).toBe('/api/admin/settings/totp');
    expect(lastCall()?.[1]?.method).toBe('GET');

    replyJsonOnce({
      ok: true,
      json: {
        issuer: 'i',
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
    await expect(
      updateTotpAdminSettings({
        issuer: 'i',
        allowedAlgorithms: ['SHA1'],
        allowedDigits: [6],
        allowedPeriodSeconds: [30],
        maxSkew: 1,
        defaultAlgorithm: 'SHA1',
        defaultDigits: 6,
        defaultPeriodSeconds: 30,
        defaultSkew: 1,
      }),
    ).resolves.toMatchObject({ issuer: 'i' });
    expect(lastCall()?.[1]?.method).toBe('PUT');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: true, json: { content: [], totalPages: 0, totalElements: 0, size: 10, number: 0 } });
    await expect(queryAdminUserTotpStatus({ page: 1, size: 10 } as any)).resolves.toMatchObject({ content: [] });
    expect(lastCall()?.[0]).toBe('/api/admin/users/totp/query');
    expect(lastCall()?.[1]?.method).toBe('POST');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyOnce({ ok: true, status: 200 });
    await expect(resetAdminUserTotp(9)).resolves.toBeUndefined();
    expect(lastCall()?.[0]).toBe('/api/admin/users/totp/9/reset');
    expect(lastCall()?.[1]?.method).toBe('POST');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });
  });

  it('totpAdminService get/update/query throw backend message and fallback', async () => {
    const { getTotpAdminSettings, updateTotpAdminSettings, queryAdminUserTotpStatus } = await import('./totpAdminService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getTotpAdminSettings()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(
      updateTotpAdminSettings({
        issuer: 'i',
        allowedAlgorithms: ['SHA1'],
        allowedDigits: [6],
        allowedPeriodSeconds: [30],
        maxSkew: 1,
        defaultAlgorithm: 'SHA1',
        defaultDigits: 6,
        defaultPeriodSeconds: 30,
        defaultSkew: 1,
      }),
    ).rejects.toThrow('保存 TOTP 策略失败');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(queryAdminUserTotpStatus({ page: 1, size: 10 } as any)).rejects.toThrow('加载用户 TOTP 状态失败');
  });

  it('passwordResetService send/reset succeed and throw fallback messages', async () => {
    const { sendPasswordResetEmailCode, resetPasswordByEmailCode, resetPasswordByTotp } = await import('./passwordResetService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 } });
    await expect(sendPasswordResetEmailCode('a@b.com')).resolves.toEqual({ message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 });
    expect(lastCall()?.[0]).toBe('/api/auth/password-reset/send-code');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(sendPasswordResetEmailCode('a@b.com')).rejects.toThrow('发送验证码失败');

    replyJsonOnce({ ok: true, json: {} });
    await expect(resetPasswordByEmailCode('a@b.com', '123', 'p')).resolves.toBeUndefined();
    expect(lastCall()?.[0]).toBe('/api/auth/password-reset/reset');
    expect(lastCall()?.[1]?.body).toBe(JSON.stringify({ email: 'a@b.com', emailCode: '123', newPassword: 'p' }));

    replyJsonOnce({ ok: true, json: {} });
    await expect(resetPasswordByTotp('a@b.com', '000000', 'p')).resolves.toBeUndefined();
    expect(lastCall()?.[0]).toBe('/api/auth/password-reset/reset');
    expect(lastCall()?.[1]?.body).toBe(JSON.stringify({ email: 'a@b.com', totpCode: '000000', newPassword: 'p' }));
  });

  it('passwordResetService covers message type guards and json parse fallback', async () => {
    const { getPasswordResetStatus, sendPasswordResetEmailCode } = await import('./passwordResetService');
    const { replyOnce, replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { message: 1, resendWaitSeconds: '1', codeTtlSeconds: null } });
    await expect(sendPasswordResetEmailCode('a@b.com')).resolves.toEqual({ message: undefined, resendWaitSeconds: undefined, codeTtlSeconds: undefined });

    replyJsonOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(sendPasswordResetEmailCode('a@b.com')).rejects.toThrow('发送验证码失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(getPasswordResetStatus('a@b.com')).rejects.toThrow('查询找回密码状态失败');
  });

  it('emailChangeService covers json parse fallback and message type guard', async () => {
    const { verifyEmailChangePassword, sendOldEmailVerificationCode, verifyOldEmailOrTotp } = await import('./emailChangeService');
    const { replyOnce, replyJsonOnce } = installFetchMock();

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(verifyEmailChangePassword('p')).rejects.toThrow('密码验证失败');

    replyJsonOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(sendOldEmailVerificationCode()).rejects.toThrow('发送验证码失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad') });
    await expect(verifyOldEmailOrTotp({ method: 'email', emailCode: '1' })).rejects.toThrow('验证失败');
  });

  it('security2faPolicyAccountService get/update cover success and error paths', async () => {
    const { getMySecurity2faPolicy, updateMyLogin2faPreference } = await import('./security2faPolicyAccountService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { totpAllowed: true } });
    await expect(getMySecurity2faPolicy()).resolves.toMatchObject({ totpAllowed: true });
    expect(lastCall()?.[0]).toBe('/api/account/security-2fa-policy');
    expect(lastCall()?.[1]?.method).toBe('GET');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(getMySecurity2faPolicy()).rejects.toThrow('加载 2FA 策略失败');

    replyJsonOnce({ ok: true, json: { login2faEnabled: true } });
    await expect(updateMyLogin2faPreference({ enabled: true, method: 'totp', totpCode: '000000' })).resolves.toMatchObject({
      login2faEnabled: true,
    });
    expect(lastCall()?.[0]).toBe('/api/account/login-2fa-preference');
    expect(lastCall()?.[1]?.method).toBe('PUT');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(lastCall()?.[1]?.body).toBe(JSON.stringify({ enabled: true, method: 'totp', totpCode: '000000' }));

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(updateMyLogin2faPreference({ enabled: false, method: 'email', emailCode: '1' })).rejects.toThrow('保存失败');
  });

  it('security2faPolicyAccountService get/update cover backend message and json parse fallback', async () => {
    const { getMySecurity2faPolicy, updateMyLogin2faPreference } = await import('./security2faPolicyAccountService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(getMySecurity2faPolicy()).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(getMySecurity2faPolicy()).rejects.toThrow('加载 2FA 策略失败');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm2' } });
    await expect(updateMyLogin2faPreference({ enabled: true, method: 'totp', totpCode: '000000' })).rejects.toThrow('m2');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(updateMyLogin2faPreference({ enabled: false, method: 'email', emailCode: '1' })).rejects.toThrow('保存失败');
  });
});
