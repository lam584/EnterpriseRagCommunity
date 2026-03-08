import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('emailChangeService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('verifyEmailChangePassword ok resolves and sends csrf header', async () => {
    const { verifyEmailChangePassword } = await import('./emailChangeService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: {} });
    await expect(verifyEmailChangePassword('p')).resolves.toBeUndefined();

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toBe('/api/account/email-change/verify-password');
    expect(info?.method).toBe('POST');
    expect(info?.init?.credentials).toBe('include');
    expect(info?.init?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(info?.body).toBe(JSON.stringify({ password: 'p' }));
  });

  it('verifyEmailChangePassword error uses backend message string else fallback', async () => {
    const { verifyEmailChangePassword } = await import('./emailChangeService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(verifyEmailChangePassword('p')).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(verifyEmailChangePassword('p')).rejects.toThrow('密码验证失败');
  });

  it('verifyEmailChangePassword json parse failure falls back', async () => {
    const { verifyEmailChangePassword } = await import('./emailChangeService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(verifyEmailChangePassword('p')).rejects.toThrow('密码验证失败');
  });

  it('sendOldEmailVerificationCode returns typed fields and errors fallback on invalid types', async () => {
    const { sendOldEmailVerificationCode } = await import('./emailChangeService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 } });
    await expect(sendOldEmailVerificationCode()).resolves.toEqual({ message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 });

    replyJsonOnce({ ok: true, json: { message: 1, resendWaitSeconds: '1', codeTtlSeconds: null } });
    await expect(sendOldEmailVerificationCode()).resolves.toEqual({
      message: undefined,
      resendWaitSeconds: undefined,
      codeTtlSeconds: undefined,
    });
  });

  it('sendOldEmailVerificationCode error uses message string else fallback; json parse fail fallback', async () => {
    const { sendOldEmailVerificationCode } = await import('./emailChangeService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'no' } });
    await expect(sendOldEmailVerificationCode()).rejects.toThrow('no');

    replyJsonOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(sendOldEmailVerificationCode()).rejects.toThrow('发送验证码失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(sendOldEmailVerificationCode()).rejects.toThrow('发送验证码失败');
  });

  it('verifyOldEmailOrTotp sends request body as-is for email/totp and throws fallback on json parse fail', async () => {
    const { verifyOldEmailOrTotp } = await import('./emailChangeService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: {} });
    await expect(verifyOldEmailOrTotp({ method: 'email', emailCode: '111111' })).resolves.toBeUndefined();
    expect(JSON.parse(String(getFetchCallInfo(lastCall())?.body || '{}'))).toEqual({ method: 'email', emailCode: '111111' });

    replyJsonOnce({ ok: true, json: {} });
    await expect(verifyOldEmailOrTotp({ method: 'totp', totpCode: '222222' })).resolves.toBeUndefined();
    expect(JSON.parse(String(getFetchCallInfo(lastCall())?.body || '{}'))).toEqual({ method: 'totp', totpCode: '222222' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(verifyOldEmailOrTotp({ method: 'email', emailCode: 'x' })).rejects.toThrow('验证失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(verifyOldEmailOrTotp({ method: 'totp', totpCode: 'x' })).rejects.toThrow('验证失败');
  });

  it('sendChangeEmailVerificationCode returns typed fields and posts newEmail', async () => {
    const { sendChangeEmailVerificationCode } = await import('./emailChangeService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 } });
    await expect(sendChangeEmailVerificationCode('a@b.com')).resolves.toEqual({ message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 });

    expect(JSON.parse(String(getFetchCallInfo(lastCall())?.body || '{}'))).toEqual({ newEmail: 'a@b.com' });
  });

  it('changeEmail error uses backend message string else fallback; json parse fail fallback', async () => {
    const { changeEmail } = await import('./emailChangeService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(changeEmail({ newEmail: 'a@b.com', newEmailCode: '1' })).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(changeEmail({ newEmail: 'a@b.com', newEmailCode: '1' })).rejects.toThrow('更换邮箱失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(changeEmail({ newEmail: 'a@b.com', newEmailCode: '1' })).rejects.toThrow('更换邮箱失败');
  });
});
