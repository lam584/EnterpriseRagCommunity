import type { SpringPage } from '../types/page';
import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';

export type PortalSearchHitType = 'POST' | 'COMMENT' | 'FILE';

export type PortalSearchHitDTO = {
  type: PortalSearchHitType;
  postId?: number | null;
  commentId?: number | null;
  fileAssetId?: number | null;
  title?: string | null;
  snippet?: string | null;
  highlightedTitle?: string | null;
  highlightedSnippet?: string | null;
  score?: number | null;
  createdAt?: string | null;
  url?: string | null;
};

function buildQueryString(query: Record<string, unknown>): string {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '' && value !== 'ALL') {
      params.append(key, String(value));
    }
  });
  return params.toString();
}

const apiUrl = serviceApiUrl;


export async function portalSearch(
  q: string,
  page = 1,
  pageSize = 20,
  boardId?: number | null,
): Promise<SpringPage<PortalSearchHitDTO>> {
  const qs = buildQueryString({ q, page, pageSize, boardId });
  const res = await fetch(apiUrl(`/api/portal/search?${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '搜索失败');
  }
  return data as SpringPage<PortalSearchHitDTO>;
}
