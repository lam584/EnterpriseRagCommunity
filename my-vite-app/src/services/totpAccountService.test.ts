import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import { disableTotp, enrollTotp, getTotpPolicy, getTotpStatus, verifyTotp, verifyTotpPassword } from './totpAccountService';

describe('totpAccountService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('enrollTotp sends csrf header and json body', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        otpauthUri: 'otpauth://totp/x',
        secretBase32: 'S',
        algorithm: 'SHA1',
        digits: 6,
        periodSeconds: 30,
        skew: 1,
      },
    });

    const res = await enrollTotp({ password: 'p', emailCode: 'e', digits: 6 });

    expect(res.secretBase32).toBe('S');
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/account/totp/enroll');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
    });
    expect(String((fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body)).toBe(
      JSON.stringify({ password: 'p', emailCode: 'e', digits: 6 }),
    );
  });

  it('verifyTotp supports overload and includes csrf header', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true } });

    const res = await verifyTotp('000000', 'pw', 'email-code');

    expect(res.enabled).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/account/totp/verify');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
    });
    expect(String((fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body)).toBe(
      JSON.stringify({ code: '000000', password: 'pw', emailCode: 'email-code' }),
    );
  });

  it('verifyTotp supports object overload', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true } });
    await expect(verifyTotp({ code: '000000', password: 'pw', emailCode: 'email-code' })).resolves.toMatchObject({ enabled: true });
    expect(String((fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body)).toBe(
      JSON.stringify({ code: '000000', password: 'pw', emailCode: 'email-code' }),
    );
  });

  it('verifyTotp falls back to default message when backend message is missing or json parse fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: null });
    await expect(verifyTotp('000000', 'pw')).rejects.toThrow('验证失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(verifyTotp('000000', 'pw')).rejects.toThrow('验证失败');
  });

  it('extracts backend messages with more shapes', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: 'err' });
    await expect(verifyTotp('000000', 'pw')).rejects.toThrow('err');

    mockFetchResponseOnce({ ok: false, status: 400, json: ['only'] });
    await expect(verifyTotp('000000', 'pw')).rejects.toThrow('only');

    mockFetchResponseOnce({ ok: false, status: 400, json: { a: '1', b: '2' } });
    await expect(verifyTotp('000000', 'pw')).rejects.toThrow(/a: 1.*b: 2|b: 2.*a: 1/);
  });

  it('disableTotp accepts string code and includes csrf header', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: false } });

    const res = await disableTotp('123456');

    expect(res.enabled).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/account/totp/disable');
    expect(String((fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body)).toBe(JSON.stringify({ code: '123456' }));
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
    });
  });

  it('disableTotp accepts object payload and falls back on json parse failure', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: false } });
    await expect(disableTotp({ method: 'totp', code: '1' })).resolves.toMatchObject({ enabled: false });
    expect(String((fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body)).toBe(JSON.stringify({ method: 'totp', code: '1' }));

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(disableTotp({ method: 'totp', code: '1' })).rejects.toThrow('停用失败');
  });

  it('verifyTotpPassword throws backend message, and falls back on json parse failure', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad password' } });
    await expect(verifyTotpPassword('ENABLE', 'p')).rejects.toThrow('bad password');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(verifyTotpPassword('DISABLE', 'p')).rejects.toThrow('密码验证失败');
  });

  it('getTotpPolicy/getTotpStatus return json on ok and use fallback message on bad json', async () => {
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
    await expect(getTotpPolicy()).resolves.toMatchObject({ issuer: 'issuer' });

    mockFetchResponseOnce({ ok: true, json: { enabled: false } });
    await expect(getTotpStatus()).resolves.toMatchObject({ enabled: false });

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(getTotpPolicy()).rejects.toThrow('加载 TOTP 策略失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(getTotpStatus()).rejects.toThrow('加载 TOTP 状态失败');
  });

  it('extracts backend messages with multiple shapes', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { code: '验证码格式不正确' } });
    await expect(verifyTotp('bad', 'p')).rejects.toThrow('验证码格式不正确');

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: '验证码不正确' } });
    await expect(verifyTotp('000000', 'p')).rejects.toThrow('验证码不正确');

    mockFetchResponseOnce({ ok: false, status: 400, json: ['a', 'b'] });
    await expect(enrollTotp()).rejects.toThrow('a；b');

    mockFetchResponseOnce({ ok: false, status: 400, json: { error: 'e' } });
    await expect(enrollTotp()).rejects.toThrow('e');

    mockFetchResponseOnce({ ok: false, status: 400, json: { detail: 'd' } });
    await expect(enrollTotp()).rejects.toThrow('d');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(enrollTotp()).rejects.toThrow('生成密钥失败');
  });
});
