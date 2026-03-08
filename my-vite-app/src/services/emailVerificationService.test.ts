import { describe, expect, it, vi, beforeEach } from 'vitest';
import { sendAccountEmailVerificationCode } from './emailVerificationService';
import { mockFetch, mockFetchJsonOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('emailVerificationService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('sendAccountEmailVerificationCode returns normalized fields', async () => {
    mockFetchJsonOnce({ ok: true, json: { message: 'ok', resendWaitSeconds: 1, codeTtlSeconds: 2 } });
    const res = await sendAccountEmailVerificationCode('P');
    expect(res.message).toBe('ok');
    expect(res.resendWaitSeconds).toBe(1);
    expect(res.codeTtlSeconds).toBe(2);
  });

  it('sendAccountEmailVerificationCode posts csrf header and purpose', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({ ok: true, json: async () => ({}) });
    await sendAccountEmailVerificationCode('ADMIN_STEP_UP');
    const init = (fetchMock as any).mock.calls[0]?.[1];
    expect(init.headers?.['X-XSRF-TOKEN']).toBe('csrf');
    expect(JSON.parse(String(init.body))).toEqual({ purpose: 'ADMIN_STEP_UP' });
  });

  it('sendAccountEmailVerificationCode throws backend message', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(sendAccountEmailVerificationCode('P')).rejects.toThrow('bad');
  });

  it('sendAccountEmailVerificationCode throws fallback when message missing', async () => {
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(sendAccountEmailVerificationCode('P')).rejects.toThrow('发送验证码失败');
  });

  it('sendAccountEmailVerificationCode normalizes optional fields', async () => {
    mockFetchJsonOnce({ ok: true, json: { message: 1, resendWaitSeconds: 'x', codeTtlSeconds: null } });
    const res = await sendAccountEmailVerificationCode('P');
    expect(res.message).toBeUndefined();
    expect(res.resendWaitSeconds).toBeUndefined();
    expect(res.codeTtlSeconds).toBeUndefined();
  });

  it('sendAccountEmailVerificationCode falls back when json parsing throws', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(sendAccountEmailVerificationCode('P')).rejects.toThrow('发送验证码失败');
  });
});
