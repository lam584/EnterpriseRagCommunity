import { describe, expect, it } from 'vitest';
import { collectMissingImageSizeEntries, hasResolvedImageSize } from './chunk-review-helpers';

describe('chunk-review-helpers', () => {
  it('hasResolvedImageSize checks positive finite width and height only', () => {
    expect(hasResolvedImageSize({ width: 320, height: 180 })).toBe(true);
    expect(hasResolvedImageSize({ width: 0, height: 180 })).toBe(false);
    expect(hasResolvedImageSize({ width: 320, height: Number.NaN })).toBe(false);
    expect(hasResolvedImageSize({ width: undefined, height: 180 })).toBe(false);
  });

  it('collectMissingImageSizeEntries keeps only url-backed images without usable size', () => {
    const missing = collectMissingImageSizeEntries([
      { url: 'https://img.example/a.png', width: null, height: 200 },
      { url: 'https://img.example/b.png', width: 120, height: 90 },
      { url: '   ', width: null, height: null },
      { url: 'https://img.example/c.png', width: 0, height: 10 },
    ]);

    expect(missing).toEqual([
      {
        img: { url: 'https://img.example/a.png', width: null, height: 200 },
        idx: 0,
        url: 'https://img.example/a.png',
      },
      {
        img: { url: 'https://img.example/c.png', width: 0, height: 10 },
        idx: 3,
        url: 'https://img.example/c.png',
      },
    ]);
  });
});
