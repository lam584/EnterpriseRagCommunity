import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearApiBaseUrlForTests, getFetchCallInfo, installFetchMock, resetServiceTest, setApiBaseUrlForTests } from '../testUtils/serviceTestHarness';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('llmRoutingAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
    clearApiBaseUrlForTests();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('adminGetLlmRoutingConfig covers ok and request shape', async () => {
    const { adminGetLlmRoutingConfig } = await import('./llmRoutingAdminService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { scenarios: [], policies: [], targets: [] } });
    await expect(adminGetLlmRoutingConfig()).resolves.toMatchObject({ scenarios: [] });

    const info = getFetchCallInfo(lastCall())!;
    expect(info.method).toBe('GET');
    expect(info.init).toMatchObject({ credentials: 'include' });
    expect(parseUrl(info.url).pathname).toBe('/api/admin/ai/routing/config');

    setApiBaseUrlForTests('https://api.example');
    replyJsonOnce({ ok: true, json: { scenarios: [] } });
    await expect(adminGetLlmRoutingConfig()).resolves.toMatchObject({ scenarios: [] });
    expect(String(lastCall()?.[0] || '')).toContain('https://api.example/api/admin/ai/routing/config');
  });

  it('adminUpdateLlmRoutingConfig covers 409 hint, normal error, and json parse fallback', async () => {
    const { adminUpdateLlmRoutingConfig } = await import('./llmRoutingAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { scenarios: [] } });
    await expect(adminUpdateLlmRoutingConfig({ scenarios: [] })).resolves.toMatchObject({ scenarios: [] });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('PUT');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });

    replyJsonOnce({ ok: false, status: 409, json: { message: 'm409' } });
    await expect(adminUpdateLlmRoutingConfig({})).rejects.toThrow('m409（请先点击“刷新”再保存）');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm400' } });
    await expect(adminUpdateLlmRoutingConfig({})).rejects.toThrow('m400');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpdateLlmRoutingConfig({})).rejects.toThrow('保存负载均衡配置失败');
  });
});
