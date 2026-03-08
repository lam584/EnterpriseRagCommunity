import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

describe('moderationLlmService', () => {
  beforeEach(() => {
    resetServiceTest();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('adminTestLlmModeration covers ok, backend message error, and json parse fallback', async () => {
    const { adminTestLlmModeration } = await import('./moderationLlmService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { decision: 'APPROVE' } });
    await expect(adminTestLlmModeration({ text: 't' })).resolves.toMatchObject({ decision: 'APPROVE' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminTestLlmModeration({ text: 't' })).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminTestLlmModeration({ text: 't' })).rejects.toThrow('Failed to run LLM moderation test');
  });

  it('adminTestLlmModeration does not attach signal when opts is omitted', async () => {
    const { adminTestLlmModeration } = await import('./moderationLlmService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { decision: 'APPROVE' } });
    await expect(adminTestLlmModeration({ text: 't' })).resolves.toMatchObject({ decision: 'APPROVE' });
    const init = getFetchCallInfo(lastCall())!.init as RequestInit;
    expect(init.signal).toBeUndefined();
  });

  it('adminTestLlmModeration covers timeout and signal branches and clears timers', async () => {
    const { adminTestLlmModeration } = await import('./moderationLlmService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    vi.useFakeTimers();
    replyJsonOnce({ ok: true, json: { decision: 'APPROVE' } });
    await expect(adminTestLlmModeration({ text: 't' }, { timeoutMs: 10 })).resolves.toMatchObject({ decision: 'APPROVE' });
    expect(vi.getTimerCount()).toBe(0);
    const init1 = getFetchCallInfo(lastCall())!.init as RequestInit;
    expect(init1.signal).toBeTruthy();

    const outer = new AbortController();
    replyJsonOnce({ ok: true, json: { decision: 'APPROVE' } });
    await expect(adminTestLlmModeration({ text: 't' }, { signal: outer.signal })).resolves.toMatchObject({ decision: 'APPROVE' });
    const init2 = getFetchCallInfo(lastCall())!.init as RequestInit;
    expect(init2.signal).toBeTruthy();

    const abortedOuter = new AbortController();
    abortedOuter.abort();
    replyJsonOnce({ ok: true, json: { decision: 'APPROVE' } });
    await expect(adminTestLlmModeration({ text: 't' }, { signal: abortedOuter.signal })).resolves.toMatchObject({ decision: 'APPROVE' });
    const init3 = getFetchCallInfo(lastCall())!.init as RequestInit;
    expect((init3.signal as AbortSignal).aborted).toBe(true);
  });

  it('adminGetLlmModerationConfig covers ok, backend message error, and json parse fallback', async () => {
    const { adminGetLlmModerationConfig } = await import('./moderationLlmService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { textPromptCode: 'p' } });
    await expect(adminGetLlmModerationConfig()).resolves.toMatchObject({ textPromptCode: 'p' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm1' } });
    await expect(adminGetLlmModerationConfig()).rejects.toThrow('m1');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetLlmModerationConfig()).rejects.toThrow('Failed to load LLM moderation config');
  });

  it('adminUpsertLlmModerationConfig sends csrf header and covers error branches', async () => {
    const { adminUpsertLlmModerationConfig } = await import('./moderationLlmService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { textPromptCode: 'p' } });
    await expect(adminUpsertLlmModerationConfig({ textPromptCode: 'p' })).resolves.toMatchObject({ textPromptCode: 'p' });
    const info1 = getFetchCallInfo(lastCall())!;
    expect(info1.method).toBe('PUT');
    expect(info1.url).toContain('/api/admin/moderation/llm/config');
    expect(info1.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
    expect(JSON.parse(String(info1.body))).toEqual({ textPromptCode: 'p' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm2' } });
    await expect(adminUpsertLlmModerationConfig({ textPromptCode: 'p' })).rejects.toThrow('m2');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpsertLlmModerationConfig({ textPromptCode: 'p' })).rejects.toThrow('Failed to save LLM moderation config');
  });
});
