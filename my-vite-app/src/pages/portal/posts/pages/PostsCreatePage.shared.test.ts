import { describe, expect, it, vi } from 'vitest';
import {
  ensureTagSlugsWithAvailableTags,
  formatUploadRetryMessage,
  normalizeRequestedTagNames,
  normalizeSuggestedLanguages,
  suggestLanguagesToPublish,
} from './PostsCreatePage.shared';
import { slugify } from '../../../../services/tagService';

describe('PostsCreatePage.shared', () => {
  it('normalizeRequestedTagNames trims blanks and applies optional limit', () => {
    expect(normalizeRequestedTagNames(['  AI  ', '', '  ', 'RAG'], 1)).toEqual(['AI']);
    expect(normalizeRequestedTagNames(['  AI  ', '', '  ', 'RAG'])).toEqual(['AI', 'RAG']);
  });

  it('ensureTagSlugsWithAvailableTags reuses existing tags and creates missing ones', async () => {
    const createTag = vi.fn(async (name: string, slug: string) => ({
      id: 2,
      usageCount: 0,
      createdAt: '2026-01-01T00:00:00Z',
      tenantId: 1,
      type: 'TOPIC' as const,
      name,
      slug,
      system: false,
      active: true,
    }));

    const result = await ensureTagSlugsWithAvailableTags({
      names: [' 已有标签 ', '新标签'],
      availableTags: [
        {
          id: 1,
          usageCount: 3,
          createdAt: '2026-01-01T00:00:00Z',
          tenantId: 1,
          type: 'TOPIC',
          name: '已有标签',
          slug: 'existing-tag',
          system: false,
          active: true,
        },
      ],
      createTag,
    });

    const expectedSlug = slugify('新标签');
    expect(createTag).toHaveBeenCalledTimes(1);
    expect(createTag).toHaveBeenCalledWith('新标签', expectedSlug);
    expect(result.slugs).toEqual(['existing-tag', expectedSlug]);
    expect(result.availableTags[0]?.slug).toBe(expectedSlug);
  });

  it('ensureTagSlugsWithAvailableTags reports create errors and continues', async () => {
    const onCreateError = vi.fn();
    const createTag = vi
      .fn()
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce({
        id: 3,
        usageCount: 0,
        createdAt: '2026-01-01T00:00:00Z',
        tenantId: 1,
        type: 'TOPIC',
        name: '可创建',
        slug: 'ke-chuang-jian',
        system: false,
        active: true,
      });

    const result = await ensureTagSlugsWithAvailableTags({
      names: ['失败标签', '可创建'],
      availableTags: [],
      createTag,
      onCreateError,
    });

    expect(onCreateError).toHaveBeenCalledTimes(1);
    expect(result.slugs).toEqual(['ke-chuang-jian']);
    expect(result.availableTags).toHaveLength(1);
  });

  it('formatUploadRetryMessage includes retry count, delay and optional request id', () => {
    expect(
      formatUploadRetryMessage({
        attempt: 2,
        maxAttempts: 5,
        delayMs: 2100,
        status: 503,
        requestId: 'req-1',
      }),
    ).toBe('重试中（2/5，3s 后）HTTP 503 requestId=req-1');

    expect(
      formatUploadRetryMessage({
        attempt: 1,
        maxAttempts: 3,
        delayMs: 200,
        status: null,
      }),
    ).toBe('重试中（1/3，1s 后）网络错误');
  });

  it('normalizeSuggestedLanguages trims and limits values', () => {
    expect(normalizeSuggestedLanguages([' 中文 ', '', null, 'en', ' fr '], 2)).toEqual(['中文', 'en']);
    expect(normalizeSuggestedLanguages('bad', 2)).toEqual([]);
  });

  it('suggestLanguagesToPublish respects enabled flag and swallows errors', async () => {
    const suggest = vi.fn().mockResolvedValue({ languages: [' 中文 ', 'en', '', 'fr'] });
    await expect(
      suggestLanguagesToPublish({
        enabled: true,
        title: ' 标题 ',
        content: ' 内容 ',
        suggest,
      }),
    ).resolves.toEqual(['中文', 'en', 'fr']);
    expect(suggest).toHaveBeenCalledWith({ title: '标题', content: '内容' });

    const disabled = vi.fn();
    await expect(
      suggestLanguagesToPublish({
        enabled: false,
        title: 'x',
        content: 'y',
        suggest: disabled as never,
      }),
    ).resolves.toEqual([]);
    expect(disabled).not.toHaveBeenCalled();

    await expect(
      suggestLanguagesToPublish({
        enabled: true,
        title: null,
        content: 'y',
        suggest: vi.fn().mockRejectedValue(new Error('boom')),
      }),
    ).resolves.toEqual([]);
  });
});
