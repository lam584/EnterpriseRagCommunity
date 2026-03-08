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

describe('promptsAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('adminBatchGetPrompts posts payload and covers message error and json parse fallback', async () => {
    const { adminBatchGetPrompts } = await import('./promptsAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { prompts: [], missingCodes: ['a'] } });
    await expect(adminBatchGetPrompts(['a'])).resolves.toMatchObject({ missingCodes: ['a'] });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('POST');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
    expect(info1.body).toBe(JSON.stringify({ codes: ['a'] }));
    expect(parseUrl(info1.url).pathname).toBe('/api/admin/prompts/batch');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminBatchGetPrompts(['a'])).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminBatchGetPrompts(['a'])).rejects.toThrow('获取提示词内容失败');
  });

  it('adminUpdatePromptContent encodes promptCode and covers message error', async () => {
    const { adminUpdatePromptContent } = await import('./promptsAdminService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { promptCode: 'a/b', version: 1 } });
    await expect(adminUpdatePromptContent('a/b', { name: 'n' })).resolves.toMatchObject({ promptCode: 'a/b' });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('PUT');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
    expect(info1.body).toBe(JSON.stringify({ name: 'n' }));
    expect(parseUrl(info1.url).pathname).toBe('/api/admin/prompts/a%2Fb/content');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm2' } });
    await expect(adminUpdatePromptContent('a/b', {})).rejects.toThrow('m2');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpdatePromptContent('a/b', {})).rejects.toThrow('保存提示词内容失败');
  });
});
