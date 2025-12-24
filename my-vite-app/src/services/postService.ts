// src/services/postService.ts

import { getCsrfToken } from '../utils/csrfUtils';
import type { UploadResult } from './uploadService';
import type { SpringPage } from '../types/page';

export type PostStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED' | 'ARCHIVED';
export type ContentFormat = 'PLAIN' | 'MARKDOWN' | 'HTML';

export interface PostCreateDTO {
  boardId: number;
  title: string;
  content: string;
  contentFormat?: ContentFormat; // default MARKDOWN
  tags?: string[];
  attachmentIds?: number[];
}

export interface PostUpdateDTO {
  boardId: number;
  title: string;
  content: string;
  contentFormat?: ContentFormat; // default MARKDOWN
  tags?: string[];
  attachmentIds?: number[];
}

export interface PostDTO extends PostCreateDTO {
  id: number;
  tenantId?: number;
  authorId?: number;
  status?: PostStatus;
  authorName?: string;
  boardName?: string;
  attachments?: Array<UploadResult & { fileAssetId?: number }>;
  commentCount?: number;
  reactionCount?: number;
  /** 收藏数 */
  favoriteCount?: number;
  /** 热度分 */
  hotScore?: number;
  /** 当前用户是否已点赞（单用户只能点赞一次） */
  likedByMe?: boolean;
  /** 当前用户是否已收藏 */
  favoritedByMe?: boolean;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
  publishedAt?: string;
}

export interface FieldError {
  fieldErrors: Record<string, string>;
}

function toFieldErrorFromBackend(data: unknown): Record<string, string> | undefined {
  // GlobalExceptionHandler for validation returns { field: message, ... }
  if (data && typeof data === 'object' && !Array.isArray(data)) {
    const obj = data as Record<string, unknown>;
    const keys = Object.keys(obj);
    if (keys.length && keys.every((k) => typeof obj[k] === 'string')) {
      return obj as Record<string, string>;
    }
  }
  return undefined;
}

export interface PostSearchQueryDTO {
  keyword?: string;
  boardId?: number;
  status?: PostStatus | 'ALL';
  authorId?: number;
  createdFrom?: string; // yyyy-mm-dd
  createdTo?: string;
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortOrderDirection?: string;
}

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

function getPageContent<T>(data: unknown): T[] | undefined {
  if (data && typeof data === 'object' && 'content' in data) {
    const content = (data as { content?: unknown }).content;
    if (Array.isArray(content)) return content as T[];
  }
  return undefined;
}

export async function createPost(payload: PostCreateDTO): Promise<PostDTO> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl('/api/posts'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({
      boardId: payload.boardId,
      title: payload.title,
      content: payload.content,
      contentFormat: payload.contentFormat ?? 'MARKDOWN',
      attachmentIds: payload.attachmentIds ?? [],
      // 将 tags 放到 metadata，后端当前用 metadata 承载扩展字段
      metadata: payload.tags ? { tags: payload.tags } : undefined,
    }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    const fe = toFieldErrorFromBackend(data);
    if (fe) {
      throw Object.assign(new Error('Validation failed'), { fieldErrors: fe } as FieldError);
    }
    throw new Error(getBackendMessage(data) || '发布失败');
  }

  return data as PostDTO;
}

export async function listPosts(): Promise<PostDTO[]> {
  // Prefer backend pagination shape: Page<PostDTO> with { content: [...] }
  const res = await fetch(apiUrl('/api/posts?page=1&pageSize=1000'), {
    method: 'GET',
    credentials: 'include',
  });

  if (!res.ok) {
    // Keep old behavior (empty) rather than hard-fail admin page if backend isn't wired yet
    return [];
  }

  const data: unknown = await res.json().catch(() => ({}));
  return getPageContent<PostDTO>(data) ?? (Array.isArray(data) ? (data as PostDTO[]) : []);
}

export async function searchPosts(query: PostSearchQueryDTO = {}): Promise<PostDTO[]> {
  const qs = buildQueryString({
    ...query,
    // Normalize status: omit when ALL
    status: query.status === 'ALL' ? undefined : query.status,
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 1000,
  });

  const res = await fetch(apiUrl(`/api/posts?${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  if (!res.ok) {
    // For now don't explode on missing endpoint
    return [];
  }

  const data: unknown = await res.json().catch(() => ({}));
  return getPageContent<PostDTO>(data) ?? (Array.isArray(data) ? (data as PostDTO[]) : []);
}

/**
 * 分页查询帖子：用于发现页/搜索页。
 * 注意：后端 page 默认从 1 开始；Spring Page 返回的 number 可能是 0-based。
 */
export async function listPostsPage(query: PostSearchQueryDTO = {}): Promise<SpringPage<PostDTO>> {
  const qs = buildQueryString({
    ...query,
    status: query.status === 'ALL' ? undefined : query.status,
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  });

  const res = await fetch(apiUrl(`/api/posts?${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '获取帖子失败');
  }

  // 兼容后端直接返回数组的情况
  if (Array.isArray(data)) {
    return {
      content: data as PostDTO[],
      totalElements: (data as PostDTO[]).length,
      totalPages: 1,
      size: (data as PostDTO[]).length,
      number: 0,
      first: true,
      last: true,
      empty: (data as PostDTO[]).length === 0,
    };
  }

  return data as SpringPage<PostDTO>;
}

export async function updatePostStatus(id: number, status: PostStatus): Promise<PostDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/posts/${id}/status`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ status }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '更新状态失败');
  }

  return data as PostDTO;
}

export async function updatePost(id: number, payload: PostUpdateDTO): Promise<PostDTO> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl(`/api/posts/${id}`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({
      boardId: payload.boardId,
      title: payload.title,
      content: payload.content,
      contentFormat: payload.contentFormat ?? 'MARKDOWN',
      attachmentIds: payload.attachmentIds ?? [],
      metadata: payload.tags ? { tags: payload.tags } : undefined,
    }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    const fe = toFieldErrorFromBackend(data);
    if (fe) {
      throw Object.assign(new Error('Validation failed'), { fieldErrors: fe } as FieldError);
    }
    throw new Error(getBackendMessage(data) || '保存修改失败');
  }

  return data as PostDTO;
}

export async function getPost(id: number): Promise<PostDTO> {
  const res = await fetch(apiUrl(`/api/posts/${id}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '加载帖子失败');
  }

  return data as PostDTO;
}

export async function deletePost(id: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/posts/${id}`), {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });

  if (!res.ok) {
    const data: unknown = await res.json().catch(() => ({}));
    throw new Error(getBackendMessage(data) || '删除失败');
  }
}

export type PostToggleResponse = {
  likedByMe?: boolean;
  favoritedByMe?: boolean;
  reactionCount?: number;
  favoriteCount?: number;
};

export async function togglePostLike(postId: number): Promise<PostToggleResponse | null> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/posts/${postId}/like`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });

  // If backend isn't ready, don't block UI; caller will optimistically update.
  if (!res.ok) return null;
  const data: unknown = await res.json().catch(() => ({}));
  if (data && typeof data === 'object') return data as PostToggleResponse;
  return null;
}

export async function togglePostFavorite(postId: number): Promise<PostToggleResponse | null> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/posts/${postId}/favorite`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });

  if (!res.ok) return null;
  const data: unknown = await res.json().catch(() => ({}));
  if (data && typeof data === 'object') return data as PostToggleResponse;
  return null;
}
