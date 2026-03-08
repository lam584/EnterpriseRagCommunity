import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('moderationPolicyService', () => {
  beforeEach(() => {
    resetServiceTest();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('adminGetModerationPolicyConfig covers ok, message error, and json parse fallback', async () => {
    const { adminGetModerationPolicyConfig } = await import('./moderationPolicyService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { id: 1, contentType: 'POST', policyVersion: 'v1', config: {} } });
    await expect(adminGetModerationPolicyConfig('POST')).resolves.toMatchObject({ id: 1 });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('GET');
    expect(info1.init).toMatchObject({ credentials: 'include' });
    const u1 = parseUrl(info1.url);
    expect(u1.pathname).toBe('/api/admin/moderation/policy/config');
    expect(u1.searchParams.get('contentType')).toBe('POST');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminGetModerationPolicyConfig('COMMENT')).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetModerationPolicyConfig('PROFILE')).rejects.toThrow('获取审核策略配置失败');

    replyOnce({ ok: true, jsonError: new Error('bad') });
    await expect(adminGetModerationPolicyConfig('POST')).resolves.toEqual({});
  });

  it('adminUpsertModerationPolicyConfig covers ok, message error, and json parse fallback', async () => {
    const { adminUpsertModerationPolicyConfig } = await import('./moderationPolicyService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { id: 2, contentType: 'POST', policyVersion: 'v2', config: {} } });
    await expect(adminUpsertModerationPolicyConfig({ contentType: 'POST', policyVersion: 'v2', config: {} })).resolves.toMatchObject({ id: 2 });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('PUT');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
    expect(info1.body).toBe(JSON.stringify({ contentType: 'POST', policyVersion: 'v2', config: {} }));

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm2' } });
    await expect(adminUpsertModerationPolicyConfig({ contentType: 'POST', policyVersion: 'v2', config: {} })).rejects.toThrow('m2');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpsertModerationPolicyConfig({ contentType: 'POST', policyVersion: 'v2', config: {} })).rejects.toThrow('保存审核策略配置失败');

    replyOnce({ ok: true, jsonError: new Error('bad') });
    await expect(adminUpsertModerationPolicyConfig({ contentType: 'POST', policyVersion: 'v2', config: {} })).resolves.toEqual({});
  });
});

