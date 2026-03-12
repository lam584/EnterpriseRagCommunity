import { describe, expect, it, vi } from 'vitest';

vi.mock('./urlUtils', () => ({
  resolveAssetUrl: (value: string | null | undefined) => (value ? `resolved:${value}` : undefined),
}));

import { buildEvidenceImageUrlMap, extractLatestRunImageUrls } from './evidenceImageMap';

describe('buildEvidenceImageUrlMap', () => {
  it('maps image attachments by ordinal and direct identifiers', () => {
    const map = buildEvidenceImageUrlMap({
      attachments: [
        { id: 9, fileAssetId: 101, fileName: 'poster', url: '/uploads/poster', mimeType: 'image/png' },
      ],
    });

    expect(map.img_1).toBe('resolved:/uploads/poster');
    expect(map['9']).toBe('resolved:/uploads/poster');
    expect(map['101']).toBe('resolved:/uploads/poster');
    expect(map.poster).toBe('resolved:/uploads/poster');
  });

  it('accepts width and height as image hints when mime and extension are missing', () => {
    const map = buildEvidenceImageUrlMap({
      attachments: [
        { id: 11, url: '/uploads/no-ext', width: 1200, height: 900 },
      ],
    });

    expect(map.img_1).toBe('resolved:/uploads/no-ext');
    expect(map['11']).toBe('resolved:/uploads/no-ext');
  });

  it('appends extra image urls after attachment images and deduplicates repeated urls', () => {
    const map = buildEvidenceImageUrlMap({
      attachments: [
        { id: 1, url: '/uploads/shared', mimeType: 'image/jpeg' },
      ],
      extraImageUrls: ['/uploads/shared', '/uploads/avatar'],
    });

    expect(map.img_1).toBe('resolved:/uploads/shared');
    expect(map.img_2).toBe('resolved:/uploads/avatar');
  });

  it('maps extracted images from attachment metadata to img_n and placeholder keys', () => {
    const map = buildEvidenceImageUrlMap({
      attachments: [
        {
          id: 4,
          url: '/uploads/source.docx',
          mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          extractedMetadataJsonSnippet: JSON.stringify({
            extractedImages: [
              {
                index: 1,
                placeholder: '[[IMAGE_1]]',
                url: '/uploads/derived-images/1.png',
              },
            ],
          }),
        },
      ],
    });

    expect(map.img_1).toBe('resolved:/uploads/derived-images/1.png');
    expect(map['[[IMAGE_1]]']).toBe('resolved:/uploads/derived-images/1.png');
  });

  it('recovers complete extracted image entries from a truncated metadata snippet', () => {
    const map = buildEvidenceImageUrlMap({
      attachments: [
        {
          id: 4,
          url: '/uploads/source.docx',
          mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          extractedMetadataJsonSnippet:
            '{"extractedImages":[{"index":1,"placeholder":"[[IMAGE_1]]","url":"/uploads/derived-images/1.png"},{"index":2,"placeholder":"[[IMAGE_2]]","url":"/uploads/derived-images/2.png"},{"index":3,"placeholder":"[[IMAGE_3]]","url":"/uploads/derived-images/3.png"',
        },
      ],
    });

    expect(map.img_1).toBe('resolved:/uploads/derived-images/1.png');
    expect(map.img_2).toBe('resolved:/uploads/derived-images/2.png');
    expect(map['[[IMAGE_2]]']).toBe('resolved:/uploads/derived-images/2.png');
    expect(map['[[IMAGE_3]]']).toBeUndefined();
  });
});

describe('extractLatestRunImageUrls', () => {
  it('collects nested latestRun image urls', () => {
    const urls = extractLatestRunImageUrls({
      steps: [
        { details: { images: ['/uploads/derived-images/1.png', '/uploads/derived-images/1.png'] } },
      ],
      meta: {
        images: [{ url: '/uploads/derived-images/2.png' }],
      },
    });

    expect(urls).toEqual(['/uploads/derived-images/1.png', '/uploads/derived-images/2.png']);
  });
});