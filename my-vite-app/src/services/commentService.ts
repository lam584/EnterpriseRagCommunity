import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';

export type CommentStatus = 'VISIBLE' | 'PENDING' | 'HIDDEN' | 'REJECTED';

export type CommentDTO = {
  id: number;
  postId: number;
  parentId?: number | null;
  authorId?: number | null;
  authorName?: string | null;
  authorAvatarUrl?: string | null;
  authorLocation?: string | null;
  likeCount?: number | null;
  likedByMe?: boolean | null;
  content: string;
  status?: CommentStatus | null;
  createdAt?: string;
  updatedAt?: string;
  metadata?: Record<string, unknown> | null;
};

export type CommentCreateRequest = {
  content: string;
  parentId?: number | null;
};

export type CommentToggleResponseDTO = {
  likedByMe: boolean;
  likeCount: number;
};

export type CommentAdminDTO = {
  id: number;
  postId: number;
  parentId?: number | null;
  authorId: number;
  authorName?: string | null;
  content: string;
  status: CommentStatus;
  isDeleted: boolean;
  createdAt: string;
  updatedAt: string;
  // optional display
  postTitle?: string | null;
  postExcerpt?: string | null;
};

export type CommentAdminQuery = {
  page?: number;
  pageSize?: number;
  postId?: number;
  authorId?: number;
  authorName?: string;
  createdFrom?: string; // ISO datetime
  createdTo?: string;   // ISO datetime
  status?: CommentStatus;
  isDeleted?: boolean;
  keyword?: string;
};

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

function buildQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    sp.set(k, String(v));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

export async function listPostComments(postId: number, page = 1, pageSize = 20, includeMinePending = false): Promise<SpringPage<CommentDTO>> {
  const res = await fetch(apiUrl(`/api/posts/${postId}/comments?page=${page}&pageSize=${pageSize}&includeMinePending=${includeMinePending ? 'true' : 'false'}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取评论失败');
  return data as SpringPage<CommentDTO>;
}

export async function createPostComment(postId: number, payload: CommentCreateRequest): Promise<CommentDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/posts/${postId}/comments`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '发表评论失败');
  return data as CommentDTO;
}

export async function toggleCommentLike(commentId: number): Promise<CommentToggleResponseDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/comments/${commentId}/like`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '点赞失败');
  return data as CommentToggleResponseDTO;
}

export async function deleteMyComment(commentId: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/comments/${commentId}`), {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '删除评论失败');
}

export async function listMyComments(page = 1, pageSize = 20, keyword?: string): Promise<SpringPage<CommentDTO>> {
  const qs = buildQuery({page, pageSize, keyword});
  const res = await fetch(apiUrl(`/api/comments/mine${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取我的评论失败');
  return data as SpringPage<CommentDTO>;
}

export async function adminListComments(query: CommentAdminQuery = {}): Promise<SpringPage<CommentAdminDTO>> {
  const qs = buildQuery({
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
    postId: query.postId,
    authorId: query.authorId,
    authorName: query.authorName,
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
    status: query.status,
    isDeleted: query.isDeleted,
    keyword: query.keyword,
  });

  const res = await fetch(apiUrl(`/api/admin/comments${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取评论列表失败');
  return data as SpringPage<CommentAdminDTO>;
}

export async function adminUpdateCommentStatus(id: number, status: CommentStatus): Promise<CommentAdminDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/comments/${id}/status`), {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ status }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '更新评论状态失败');
  return data as CommentAdminDTO;
}

export async function adminSetCommentDeleted(id: number, isDeleted: boolean): Promise<CommentAdminDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/comments/${id}/deleted`), {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ isDeleted }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '更新删除状态失败');
  return data as CommentAdminDTO;
}
