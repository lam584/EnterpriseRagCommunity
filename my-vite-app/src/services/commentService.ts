import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';

export type CommentDTO = {
  id: number;
  postId: number;
  parentId?: number | null;
  authorId?: number | null;
  authorName?: string | null;
  content: string;
  status?: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type CommentCreateRequest = {
  content: string;
  parentId?: number | null;
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

export async function listPostComments(postId: number, page = 1, pageSize = 20): Promise<SpringPage<CommentDTO>> {
  const res = await fetch(apiUrl(`/api/posts/${postId}/comments?page=${page}&pageSize=${pageSize}`), {
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

