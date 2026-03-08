import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('passwordResetService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('getPasswordResetStatus normalizes booleans and message nullish', async () => {
    const { getPasswordResetStatus } = await import('./passwordResetService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { allowed: 1, totpEnabled: 0, emailEnabled: true, message: undefined } });
    await expect(getPasswordResetStatus('a@b.com')).resolves.toEqual({
      allowed: true,
      totpEnabled: false,
      emailEnabled: true,
      message: null,
    });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toBe('/api/auth/password-reset/status');
    expect(info?.method).toBe('POST');
    expect(info?.init?.credentials).toBe('include');
    expect(info?.init?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(JSON.parse(String(info?.body))).toEqual({ email: 'a@b.com' });
  });

  it('getPasswordResetStatus shows Boolean() type traps for string inputs', async () => {
    const { getPasswordResetStatus } = await import('./passwordResetService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { allowed: '0', totpEnabled: 'false', emailEnabled: '', message: null } });
    await expect(getPasswordResetStatus('a@b.com')).resolves.toEqual({
      allowed: true,
      totpEnabled: true,
      emailEnabled: false,
      message: null,
    });
  });

  it('getPasswordResetStatus error prefers backend message and falls back on json parse failure', async () => {
    const { getPasswordResetStatus } = await import('./passwordResetService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getPasswordResetStatus('a@b.com')).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(getPasswordResetStatus('a@b.com')).rejects.toThrow('查询找回密码状态失败');
  });

  it('getPasswordResetStatus error may stringify non-string message', async () => {
    const { getPasswordResetStatus } = await import('./passwordResetService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: { a: 1 } } });
    await expect(getPasswordResetStatus('a@b.com')).rejects.toThrow('[object Object]');
  });

  it('resetPasswordByTotp posts totpCode and uses default message on json parse failure', async () => {
    const { resetPasswordByTotp } = await import('./passwordResetService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: {} });
    await expect(resetPasswordByTotp('a@b.com', '000000', 'p')).resolves.toBeUndefined();
    expect(JSON.parse(String(getFetchCallInfo(lastCall())?.body))).toEqual({ email: 'a@b.com', totpCode: '000000', newPassword: 'p' });

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(resetPasswordByTotp('a@b.com', '000000', 'p')).rejects.toThrow('重置密码失败');
  });

  it('resetPasswordByEmailCode posts emailCode and uses backend message on failure', async () => {
    const { resetPasswordByEmailCode } = await import('./passwordResetService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: {} });
    await expect(resetPasswordByEmailCode('a@b.com', '111111', 'p')).resolves.toBeUndefined();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(resetPasswordByEmailCode('a@b.com', '111111', 'p')).rejects.toThrow('bad');
  });

  it('sendPasswordResetEmailCode returns typed fields and errors fallback on invalid types', async () => {
    const { sendPasswordResetEmailCode } = await import('./passwordResetService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 } });
    await expect(sendPasswordResetEmailCode('a@b.com')).resolves.toEqual({ message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 });

    replyJsonOnce({ ok: true, json: { message: 1, resendWaitSeconds: '1', codeTtlSeconds: null } });
    await expect(sendPasswordResetEmailCode('a@b.com')).resolves.toEqual({
      message: undefined,
      resendWaitSeconds: undefined,
      codeTtlSeconds: undefined,
    });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(sendPasswordResetEmailCode('a@b.com')).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 400, json: { message: { a: 1 } } });
    await expect(sendPasswordResetEmailCode('a@b.com')).rejects.toThrow('发送验证码失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(sendPasswordResetEmailCode('a@b.com')).rejects.toThrow('发送验证码失败');
  });
});
