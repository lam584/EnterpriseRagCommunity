// src/services/hotService.ts

import type { SpringPage } from '../types/page';
import type { PostDTO } from './postService';

export type HotWindow = '24h' | '7d' | 'all';

export interface HotPostDTO {
  post: PostDTO;
  score: number;
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

export async function fetchHotPosts(params: {
  window?: HotWindow;
  page?: number;
  pageSize?: number;
} = {}): Promise<SpringPage<HotPostDTO>> {
  const w = params.window ?? '24h';
  const page = params.page ?? 1;
  const pageSize = params.pageSize ?? 10;

  const qs = new URLSearchParams({ window: w, page: String(page), pageSize: String(pageSize) }).toString();
  const res = await fetch(apiUrl(`/api/hot?${qs}`), { method: 'GET', credentials: 'include' });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取热榜失败');
  return data as SpringPage<HotPostDTO>;
}

