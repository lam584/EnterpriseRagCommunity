import type { SpringPage } from '../types/page';

export type PortalSearchHitType = 'POST' | 'COMMENT';

export type PortalSearchHitDTO = {
  type: PortalSearchHitType;
  postId?: number | null;
  commentId?: number | null;
  title?: string | null;
  snippet?: string | null;
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

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

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

