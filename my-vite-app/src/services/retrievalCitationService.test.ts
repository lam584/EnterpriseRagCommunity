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

describe('retrievalCitationService', () => {
  beforeEach(() => {
    resetServiceTest();
    clearApiBaseUrlForTests();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('adminGetCitationConfig covers ok, base url, and json parse fallback', async () => {
    const { adminGetCitationConfig } = await import('./retrievalCitationService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { enabled: true } });
    await expect(adminGetCitationConfig()).resolves.toMatchObject({ enabled: true });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('GET');
    expect(info1.init).toMatchObject({ credentials: 'include' });
    expect(parseUrl(info1.url).pathname).toBe('/api/admin/retrieval/citation/config');

    setApiBaseUrlForTests('https://api.example');
    replyJsonOnce({ ok: true, json: { enabled: false } });
    await expect(adminGetCitationConfig()).resolves.toMatchObject({ enabled: false });
    expect(String(lastCall()?.[0] || '')).toContain('https://api.example/api/admin/retrieval/citation/config');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetCitationConfig()).rejects.toThrow('获取引用配置失败');
  });

  it('adminUpdateCitationConfig sends PUT with csrf header and throws backend message', async () => {
    const { adminUpdateCitationConfig } = await import('./retrievalCitationService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { enabled: true } });
    await expect(adminUpdateCitationConfig({ enabled: true })).resolves.toMatchObject({ enabled: true });
    const info = getFetchCallInfo(lastCall())!;
    expect(info.method).toBe('PUT');
    expect(info.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpdateCitationConfig({ enabled: true })).rejects.toThrow('bad');
  });

  it('adminTestCitation sends POST and falls back on json parse failure', async () => {
    const { adminTestCitation } = await import('./retrievalCitationService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { sourcesPreview: 's' } });
    await expect(adminTestCitation({ useSavedConfig: true })).resolves.toMatchObject({ sourcesPreview: 's' });

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminTestCitation({ useSavedConfig: true })).rejects.toThrow('引用配置测试失败');
  });
});

