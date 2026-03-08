import { afterEach, describe, expect, it } from 'vitest';
import { formatPostTime, getPostCoverThumbUrl, getPostExcerpt } from './postMeta';

describe('postMeta', () => {
  afterEach(() => {
    delete (globalThis as any).__VITE_API_BASE_URL__;
  });

  it('resolves cover thumbnail url from multiple metadata fallbacks', () => {
    (globalThis as any).__VITE_API_BASE_URL__ = 'https://api.example.com';
    expect(getPostCoverThumbUrl({ metadata: { cover: { thumbUrl: '/uploads/a.png' } } })).toBe(
      'https://api.example.com/uploads/a.png',
    );
    expect(getPostCoverThumbUrl({ metadata: { cover: { thumbnailUrl: '/uploads/b.png' } } })).toBe(
      'https://api.example.com/uploads/b.png',
    );
    expect(getPostCoverThumbUrl({ metadata: { attachments: [{ url: '/uploads/c.png' }] } })).toBe(
      'https://api.example.com/uploads/c.png',
    );
  });

  it('returns undefined when no usable cover url is present', () => {
    expect(getPostCoverThumbUrl({ metadata: {} })).toBeUndefined();
    expect(getPostCoverThumbUrl({ metadata: { cover: { thumbUrl: 123 } } as any })).toBeUndefined();
  });

  it('formats post time with fallbacks', () => {
    expect(formatPostTime({ publishedAt: undefined, createdAt: undefined })).toBe('');
    expect(formatPostTime({ publishedAt: 'not-a-date', createdAt: undefined })).toBe('not-a-date');
  });

  it('formats post time for valid dates', () => {
    const raw = '2026-01-01T00:00:00Z';
    const out = formatPostTime({ publishedAt: raw, createdAt: undefined });
    expect(out).not.toBe('');
    expect(out).not.toBe(raw);
  });

  it('builds a plain excerpt and truncates with ellipsis', () => {
    const input = ['# Title', '', '```ts', 'const x = 1', '```', '', 'hello `code` world'].join('\n');
    expect(getPostExcerpt(input, 999)).toBe('Title hello world');
    expect(getPostExcerpt('a '.repeat(200), 10)).toBe('a a a a a…');
  });

  it('returns empty excerpt for empty content', () => {
    expect(getPostExcerpt(undefined)).toBe('');
  });
});
