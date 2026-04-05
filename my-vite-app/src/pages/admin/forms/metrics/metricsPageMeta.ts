type SpringPageMetaLike = {
  totalPages?: unknown;
  totalElements?: unknown;
  number?: unknown;
  last?: boolean | null;
  content?: unknown[] | null;
};

function toNonNegativeNumber(value: unknown): number | null {
  const parsed = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return null;
  }
  return parsed;
}

export type MetricsPageMeta = {
  totalPages: number;
  totalElements: number;
  hasNextPage: boolean;
};

export function resolveMetricsPageMeta(page: number, pageSize: number, res: SpringPageMetaLike): MetricsPageMeta {
  const totalPages = Math.max(1, toNonNegativeNumber(res.totalPages) ?? 1);
  const totalElements = Math.max(0, toNonNegativeNumber(res.totalElements) ?? 0);
  const rawNumber = toNonNegativeNumber(res.number);
  const backendPageIndex =
    rawNumber == null ? Math.max(0, page - 1) : rawNumber >= 1 && rawNumber <= totalPages ? Math.max(0, rawNumber - 1) : Math.max(0, rawNumber);
  const hasMoreByFlag = res.last === false;
  const hasMoreByPageCount = backendPageIndex + 1 < totalPages;
  const hasMoreByTotal = totalElements > (backendPageIndex + 1) * pageSize;
  const hasMoreByContent = res.last !== true && (res.content ?? []).length >= pageSize;

  return {
    totalPages,
    totalElements,
    hasNextPage: hasMoreByFlag || hasMoreByPageCount || hasMoreByTotal || hasMoreByContent,
  };
}
