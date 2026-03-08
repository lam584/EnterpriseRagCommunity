import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('p1AiLightServices', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('covers ai* services happy paths and one error branch', async () => {
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: true, json: { enabled: true } });
    const { getAiChatOptions } = await import('./aiChatOptionsService');
    await expect(getAiChatOptions()).resolves.toMatchObject({ enabled: true });

    replyJsonOnce({ ok: true, json: { tags: ['t1'] } });
    const { suggestPostTags } = await import('./aiTagService');
    await expect(suggestPostTags({ content: 'c' })).resolves.toMatchObject({ tags: ['t1'] });

    replyJsonOnce({ ok: true, json: { titles: ['x'] } });
    const { suggestPostTitles } = await import('./aiTitleService');
    await expect(suggestPostTitles({ content: 'c' } as any)).resolves.toMatchObject({ titles: ['x'] });

    replyJsonOnce({ ok: true, json: { labels: ['zh'] } });
    const { suggestPostLangLabels } = await import('./aiLangLabelService');
    await expect(suggestPostLangLabels({ content: 'c' } as any)).resolves.toMatchObject({ labels: ['zh'] });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'no' } });
    const { adminProbeModel } = await import('./aiModelProbeAdminService');
    await expect(adminProbeModel('CHAT', 'p', 'm')).rejects.toThrow('no');
  });

  it('covers aiTag/aiTitle error branches', async () => {
    const { replyOnce, replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'tag bad' } });
    const { suggestPostTags } = await import('./aiTagService');
    await expect(suggestPostTags({ content: 'c' })).rejects.toThrow('tag bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(suggestPostTags({ content: 'c' })).rejects.toThrow('生成标签失败');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'title bad' } });
    const { suggestPostTitles } = await import('./aiTitleService');
    await expect(suggestPostTitles({ content: 'c' } as any)).rejects.toThrow('title bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(suggestPostTitles({ content: 'c' } as any)).rejects.toThrow('生成标题失败');
  });

  it('covers aiLangLabelService config and error fallback branches', async () => {
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();
    const { getLangLabelGenConfig, suggestPostLangLabels } = await import('./aiLangLabelService');

    replyJsonOnce({ ok: true, json: { enabled: true, maxContentChars: 1 } });
    await expect(getLangLabelGenConfig()).resolves.toMatchObject({ enabled: true });
    expect(getFetchCallInfo(lastCall())?.method).toBe('GET');
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/ai/posts/lang-label-gen/config');

    replyJsonOnce({ ok: true, json: { languages: ['zh'] } });
    await expect(suggestPostLangLabels({ content: 'c' })).resolves.toMatchObject({ languages: ['zh'] });
    expect(getFetchCallInfo(lastCall())?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf' });

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getLangLabelGenConfig()).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad-json') });
    await expect(getLangLabelGenConfig()).rejects.toThrow('获取语言标签配置失败');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(suggestPostLangLabels({ content: 'c' })).rejects.toThrow('生成语言标签失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad-json') });
    await expect(suggestPostLangLabels({ content: 'c' })).rejects.toThrow('生成语言标签失败');
  });
});
