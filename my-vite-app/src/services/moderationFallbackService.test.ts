import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('moderationFallbackService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('getFallbackConfig covers ok and error branches', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();
    const { getFallbackConfig } = await import('./moderationFallbackService');

    replyJsonOnce({ ok: true, json: { llmEnabled: true, llmHighAction: 'REJECT' } });
    await expect(getFallbackConfig()).resolves.toMatchObject({ llmEnabled: true });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getFallbackConfig()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 500, json: { message: 1 } });
    await expect(getFallbackConfig()).rejects.toThrow('加载置信回退配置失败');

    replyJsonOnce({ ok: false, status: 500, json: null });
    await expect(getFallbackConfig()).rejects.toThrow('加载置信回退配置失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(getFallbackConfig()).rejects.toThrow('加载置信回退配置失败');
  });

  it('updateFallbackConfig covers csrf header, ok, and error branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { updateFallbackConfig } = await import('./moderationFallbackService');

    replyJsonOnce({ ok: true, json: { llmEnabled: false, llmOffendingHitAction: 'HUMAN' } });
    await expect(updateFallbackConfig({ llmEnabled: false })).resolves.toMatchObject({ llmEnabled: false });
    const info = getFetchCallInfo(lastCall());
    expect(info?.method).toBe('PUT');
    expect((info?.headers as any)?.['X-XSRF-TOKEN']).toBe('csrf');
    expect(JSON.parse(String(info?.body))).toEqual({ llmEnabled: false });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(updateFallbackConfig({ llmEnabled: false })).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(updateFallbackConfig({ llmEnabled: false })).rejects.toThrow('保存置信回退配置失败');

    const csrfUtils = await import('../utils/csrfUtils');
    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf-bad'));
    await expect(updateFallbackConfig({ llmEnabled: false })).rejects.toThrow('csrf-bad');
  });
});


