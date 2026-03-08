import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('dependencyCircuitBreakerAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminGet/updateDependencyCircuitBreakerConfig allow empty dependency string', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    const { adminGetDependencyCircuitBreakerConfig, adminUpdateDependencyCircuitBreakerConfig } = await import('./dependencyCircuitBreakerAdminService');

    replyJsonOnce({ ok: true, json: {} });
    await expect(adminGetDependencyCircuitBreakerConfig('' as any)).resolves.toEqual({});
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/admin/safety/dependency-circuit-breakers/');

    replyJsonOnce({ ok: true, json: {} });
    await expect(adminUpdateDependencyCircuitBreakerConfig('' as any, {}, 'r')).resolves.toEqual({});
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/admin/safety/dependency-circuit-breakers/');
  });

  it('adminGetDependencyCircuitBreakerConfig covers dependency trim/encode and error branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminGetDependencyCircuitBreakerConfig } = await import('./dependencyCircuitBreakerAdminService');

    replyJsonOnce({ ok: true, json: { dependency: 'a/b' } });
    await expect(adminGetDependencyCircuitBreakerConfig('  a/b  ')).resolves.toMatchObject({ dependency: 'a/b' });
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/admin/safety/dependency-circuit-breakers/a%2Fb');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetDependencyCircuitBreakerConfig('x')).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 500, json: { x: 1 } });
    await expect(adminGetDependencyCircuitBreakerConfig('x')).rejects.toThrow('加载依赖熔断配置失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetDependencyCircuitBreakerConfig('x')).rejects.toThrow('加载依赖熔断配置失败');
  });

  it('adminUpdateDependencyCircuitBreakerConfig covers payload/reason defaults, ok, and errors', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { adminUpdateDependencyCircuitBreakerConfig } = await import('./dependencyCircuitBreakerAdminService');

    replyJsonOnce({ ok: true, json: { failureThreshold: 1 } });
    await expect(adminUpdateDependencyCircuitBreakerConfig('x', undefined as any, undefined as any)).resolves.toMatchObject({ failureThreshold: 1 });
    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('PUT');
    expect((info?.headers as any)?.['X-XSRF-TOKEN']).toBe('csrf');
    expect(JSON.parse(String(info?.body))).toEqual({ config: {}, reason: '' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpdateDependencyCircuitBreakerConfig('x', { cooldownSeconds: 1 }, 'r')).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpdateDependencyCircuitBreakerConfig('x', { cooldownSeconds: 1 }, 'r')).rejects.toThrow('保存依赖熔断配置失败');

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(adminUpdateDependencyCircuitBreakerConfig('x', { cooldownSeconds: 1 }, 'r')).rejects.toThrow('csrf-bad');
  });
});
