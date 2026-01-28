import { getCsrfToken } from '../utils/csrfUtils';

export type PostReportRequest = {
  reasonCode: string;
  reasonText?: string;
};

export type PostReportResponse = {
  reportId: number;
  queueId: number;
};

export type CommentReportRequest = PostReportRequest;
export type CommentReportResponse = PostReportResponse;

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

export async function reportPost(postId: number, payload: PostReportRequest): Promise<PostReportResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl(`/api/posts/${postId}/report`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({
      reasonCode: payload.reasonCode,
      reasonText: payload.reasonText,
    }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401) throw new Error('请先登录后再举报');
    throw new Error(getBackendMessage(data) || '举报失败');
  }
  return data as PostReportResponse;
}

export async function reportComment(commentId: number, payload: CommentReportRequest): Promise<CommentReportResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl(`/api/comments/${commentId}/report`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({
      reasonCode: payload.reasonCode,
      reasonText: payload.reasonText,
    }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401) throw new Error('请先登录后再举报');
    throw new Error(getBackendMessage(data) || '举报失败');
  }
  return data as CommentReportResponse;
}
