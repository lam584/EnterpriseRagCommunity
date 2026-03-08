import { afterEach, describe, expect, it } from 'vitest';
import { isAbsoluteUrl, resolveAssetUrl } from './urlUtils';

describe('urlUtils', () => {
  afterEach(() => {
    delete (globalThis as any).__VITE_API_BASE_URL__;
  });

  it('detects absolute urls', () => {
    expect(isAbsoluteUrl('http://x')).toBe(true);
    expect(isAbsoluteUrl('https://x')).toBe(true);
    expect(isAbsoluteUrl('data:image/png;base64,aaa')).toBe(true);
    expect(isAbsoluteUrl('/uploads/a.png')).toBe(false);
  });

  it('returns undefined for empty input and keeps data/blob', () => {
    expect(resolveAssetUrl(undefined)).toBeUndefined();
    expect(resolveAssetUrl(null)).toBeUndefined();
    expect(resolveAssetUrl('')).toBeUndefined();
    expect(resolveAssetUrl({ toString: () => '' } as any)).toBeUndefined();
    expect(resolveAssetUrl('data:aaa')).toBe('data:aaa');
    expect(resolveAssetUrl('blob:aaa')).toBe('blob:aaa');
  });

  it('returns absolute urls unchanged', () => {
    expect(resolveAssetUrl('https://example.com/a.png')).toBe('https://example.com/a.png');
  });

  it('resolves /uploads with api base when configured', () => {
    (globalThis as any).__VITE_API_BASE_URL__ = 'https://api.example.com';
    expect(resolveAssetUrl('/uploads/a.png')).toBe('https://api.example.com/uploads/a.png');
  });

  it('returns raw string when not a /uploads path', () => {
    (globalThis as any).__VITE_API_BASE_URL__ = 'https://api.example.com';
    expect(resolveAssetUrl('/x.png')).toBe('/x.png');
  });
});
