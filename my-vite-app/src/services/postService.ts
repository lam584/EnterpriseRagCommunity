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
  /** 当前用户是否已收藏（用于收藏列表：true 表示只查收藏） */
  favoritedByMe?: boolean;
}

export interface SearchPostsOptions {
  /**
   * 当 status === 'ALL' 时，是否仍然把 status=ALL 传给后端。
   *
   * 背景：后端 /api/posts 在不传 status 时会默认 status=PUBLISHED；
   * 管理端想看“全部状态”时必须显式传 status=ALL，才能取消状态过滤。
   *
   * 默认 false：保持旧行为（ALL 等价于不传）。
   */
  preserveAllStatus?: boolean;
}

export interface RequestOptions {
  /** 用于在路由切换/组件卸载时取消请求，避免请求堆积 */
  signal?: AbortSignal;
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

export async function listPosts(options: RequestOptions = {}): Promise<PostDTO[]> {
  // Prefer backend pagination shape: Page<PostDTO> with { content: [...] }
  const res = await fetch(apiUrl('/api/posts?page=1&pageSize=25'), {
    method: 'GET',
    credentials: 'include',
    signal: options.signal,
  });

  if (!res.ok) {
    // Keep old behavior (empty) rather than hard-fail admin page if backend isn't wired yet
    return [];
  }

  const data: unknown = await res.json().catch(() => ({}));
  return getPageContent<PostDTO>(data) ?? (Array.isArray(data) ? (data as PostDTO[]) : []);
}

export async function searchPosts(
  query: PostSearchQueryDTO = {},
  options: SearchPostsOptions & RequestOptions = {},
): Promise<PostDTO[]> {
  const preserveAllStatus = options.preserveAllStatus === true;

  const qs = buildQueryString({
    ...query,
    // Normalize status:
    // - 默认：ALL => omit（兼容旧逻辑）
    // - 管理端：ALL => keep（确保后端不会默认 PUBLISHED）
    status: query.status === 'ALL' && !preserveAllStatus ? undefined : query.status,
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 25,
  });

  const res = await fetch(apiUrl(`/api/posts?${qs}`), {
    method: 'GET',
    credentials: 'include',
    signal: options.signal,
  });

  if (!res.ok) {
    // For now don't explode on missing endpoint
    return [];
  }

  const data: unknown = await res.json().catch(() => ({}));
  return getPageContent<PostDTO>(data) ?? (Array.isArray(data) ? (data as PostDTO[]) : []);
}

/**
 * 管理端帖子查询（推荐）：走 /api/admin/posts。
 *
 * 说明：
 * - 管理端默认需要“ALL（不过滤状态）”，因此这里不会把 ALL 过滤掉。
 * - 后端接口会把不传/ALL 解释为不过滤 status。
 */
export async function searchAdminPosts(query: PostSearchQueryDTO = {}, options: RequestOptions = {}): Promise<PostDTO[]> {
  const qs = buildQueryString({
    ...query,
    // admin API: allow status=ALL to pass through
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 25,
  });

  const res = await fetch(apiUrl(`/api/admin/posts?${qs}`), {
    method: 'GET',
    credentials: 'include',
    signal: options.signal,
  });

  if (!res.ok) {
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

/**
 * 分页查询“我的帖子”（包含待审核/草稿等状态）。
 * 后端接口：GET /api/posts/mine
 *
 * 约定：
 * - authorId 不需要也不允许前端传入；后端从会话解析当前用户。
 * - status 传 ALL/undefined 表示不过滤状态（返回该用户全部帖子）。
 */
export async function listMyPostsPage(query: Omit<PostSearchQueryDTO, 'authorId'> = {}): Promise<SpringPage<PostDTO>> {
  const qs = buildQueryString({
    ...query,
    status: query.status === 'ALL' ? undefined : query.status,
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  });

  const res = await fetch(apiUrl(`/api/posts/mine?${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '获取我的帖子失败');
  }

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

/**
 * 分页查询“我收藏的帖子”。
 * 后端接口：GET /api/posts/bookmarks?page=1&pageSize=20
 */
export async function listMyBookmarkedPostsPage(
  query: Pick<PostSearchQueryDTO, 'page' | 'pageSize'> = {}
): Promise<SpringPage<PostDTO>> {
  const qs = buildQueryString({
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  });

  const res = await fetch(apiUrl(`/api/posts/bookmarks?${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '获取收藏失败');
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

/**
 * 兼容旧实现：通过 /api/posts 的查询参数过滤收藏。
 * 注意：后端目前不支持 favoritedByMe 过滤，因此不要再用于“收藏管理”页。
 */
export async function listBookmarkedPostsPage(
  query: Omit<PostSearchQueryDTO, 'favoritedByMe'> = {}
): Promise<SpringPage<PostDTO>> {
  return listPostsPage({
    ...query,
    favoritedByMe: true,
  });
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

export async function deletePostFavorite(postId: number): Promise<PostToggleResponse | null> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/posts/${postId}/favorite`), {
    method: 'DELETE',
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
