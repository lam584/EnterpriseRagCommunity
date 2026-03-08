import { describe, expect, it, vi, beforeEach } from 'vitest';
import { clearAdminStepUp, getAdminStepUpStatus, verifyAdminStepUp } from './adminStepUpService';
import { mockFetch, mockFetchJsonOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('adminStepUpService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('getAdminStepUpStatus maps fields and normalizes methods', async () => {
    mockFetchJsonOnce({ ok: true, json: { ok: false, methods: ['email', 1], ttlSeconds: 9 } });
    const res = await getAdminStepUpStatus();
    expect(res.ok).toBe(false);
    expect(res.ttlSeconds).toBe(9);
    expect(res.methods).toEqual(['email', '1']);
  });

  it('getAdminStepUpStatus throws backend message when not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(getAdminStepUpStatus()).rejects.toThrow('bad');
  });

  it('verifyAdminStepUp posts csrf header', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({ ok: true, json: async () => ({ ok: true }) });
    const res = await verifyAdminStepUp({ method: 'totp', code: '1' });
    expect(res.ok).toBe(true);
    const init = (fetchMock as any).mock.calls[0]?.[1];
    expect(init.headers?.['X-XSRF-TOKEN']).toBe('csrf');
  });

  it('clearAdminStepUp posts csrf header', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({ ok: true, json: async () => ({}) });
    await clearAdminStepUp();
    const init = (fetchMock as any).mock.calls[0]?.[1];
    expect(init.headers?.['X-XSRF-TOKEN']).toBe('csrf');
  });

  it('getAdminStepUpStatus throws fallback when no message', async () => {
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(getAdminStepUpStatus()).rejects.toThrow('获取 step-up 状态失败');
  });

  it('verifyAdminStepUp throws fallback when no message', async () => {
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(verifyAdminStepUp({ method: 'email', code: '1' })).rejects.toThrow('二次验证失败');
  });

  it('getAdminStepUpStatus covers type normalization branches', async () => {
    mockFetchJsonOnce({ ok: true, json: { ok: true, okUntilEpochMs: 1, ttlSeconds: 'x', methods: 'no', emailOtpAllowed: false } });
    const res = await getAdminStepUpStatus();
    expect(res.ok).toBe(true);
    expect(res.okUntilEpochMs).toBe(1);
    expect(res.ttlSeconds).toBeUndefined();
    expect(res.methods).toEqual([]);
    expect(res.emailOtpAllowed).toBe(false);
  });

  it('verifyAdminStepUp throws backend message when not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(verifyAdminStepUp({ method: 'email', code: '1' })).rejects.toThrow('bad');
  });

  it('getAdminStepUpStatus and verifyAdminStepUp fall back when json parsing fails', async () => {
    mockFetchResponseOnce({ ok: true, jsonError: new Error('bad json') });
    await expect(getAdminStepUpStatus()).resolves.toMatchObject({ ok: false, methods: [] });

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(verifyAdminStepUp({ method: 'totp', code: '1' })).rejects.toThrow('二次验证失败');
  });
});
