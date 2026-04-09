// src/services/hotService.ts

import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';
import type { SpringPage } from '../types/page';
import type { PostDTO } from './postService';

export type HotWindow = '24h' | '7d' | '30d' | '3m' | '6m' | '1y' | 'all';

export interface HotPostDTO {
  post: PostDTO;
  score: number;
}

const apiUrl = serviceApiUrl;

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
