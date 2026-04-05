import { getCsrfToken } from '../utils/csrfUtils';
import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';

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
export type ProfileReportRequest = PostReportRequest;
export type ProfileReportResponse = PostReportResponse;

const apiUrl = serviceApiUrl;


export async function reportPost(postId: number, payload: PostReportRequest): Promise<PostReportResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl(`api/posts/${postId}/report`), {
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

  const res = await fetch(apiUrl(`api/comments/${commentId}/report`), {
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

export async function reportProfile(userId: number, payload: ProfileReportRequest): Promise<ProfileReportResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl(`api/users/${userId}/report`), {
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
  return data as ProfileReportResponse;
}
