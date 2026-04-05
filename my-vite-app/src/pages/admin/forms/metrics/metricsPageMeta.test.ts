import { describe, expect, it } from 'vitest';
import { resolveMetricsPageMeta } from './metricsPageMeta';

describe('resolveMetricsPageMeta', () => {
  it('keeps next page available when backend total pages are wrong but last=false and page is full', () => {
    expect(
      resolveMetricsPageMeta(1, 15, {
        totalPages: 0,
        totalElements: 0,
        number: 0,
        last: false,
        content: Array.from({ length: 15 }),
      }),
    ).toEqual({
      totalPages: 1,
      totalElements: 0,
      hasNextPage: true,
    });
  });

  it('supports one-based backend page numbers', () => {
    expect(
      resolveMetricsPageMeta(2, 15, {
        totalPages: 2,
        totalElements: 16,
        number: 2,
        last: true,
        content: [{}],
      }),
    ).toEqual({
      totalPages: 2,
      totalElements: 16,
      hasNextPage: false,
    });
  });

  it('falls back to total count when last flag is missing', () => {
    expect(
      resolveMetricsPageMeta(1, 10, {
        totalPages: 1,
        totalElements: 21,
        number: 0,
        content: Array.from({ length: 10 }),
      }),
    ).toEqual({
      totalPages: 1,
      totalElements: 21,
      hasNextPage: true,
    });
  });
});
