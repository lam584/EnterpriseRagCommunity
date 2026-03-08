import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('contentSafetyCircuitBreakerAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminGetContentSafetyCircuitBreakerStatus covers ok and error branches', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();
    const { adminGetContentSafetyCircuitBreakerStatus } = await import('./contentSafetyCircuitBreakerAdminService');

    replyJsonOnce({ ok: true, json: { persisted: true } });
    await expect(adminGetContentSafetyCircuitBreakerStatus()).resolves.toMatchObject({ persisted: true });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetContentSafetyCircuitBreakerStatus()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 500, json: { x: 1 } });
    await expect(adminGetContentSafetyCircuitBreakerStatus()).rejects.toThrow('加载熔断状态失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetContentSafetyCircuitBreakerStatus()).rejects.toThrow('加载熔断状态失败');
  });

  it('adminUpdateContentSafetyCircuitBreakerConfig covers reason default, ok, and error branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminUpdateContentSafetyCircuitBreakerConfig } = await import('./contentSafetyCircuitBreakerAdminService');

    replyJsonOnce({ ok: true, json: { config: { enabled: true } } });
    await expect(adminUpdateContentSafetyCircuitBreakerConfig(undefined as any, undefined as any)).resolves.toMatchObject({ config: { enabled: true } });
    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('PUT');
    expect((info?.headers as any)?.['X-XSRF-TOKEN']).toBe('csrf');
    expect(JSON.parse(String(info?.body))).toEqual({ config: {}, reason: '' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpdateContentSafetyCircuitBreakerConfig({ enabled: true } as any, 'r')).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpdateContentSafetyCircuitBreakerConfig({ enabled: true } as any, 'r')).rejects.toThrow('保存熔断配置失败');

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(adminUpdateContentSafetyCircuitBreakerConfig({ enabled: true } as any, 'r')).rejects.toThrow('csrf-bad');
  });
});

