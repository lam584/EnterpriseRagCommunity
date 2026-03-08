import { getCsrfToken } from '../utils/csrfUtils';

function getApiBase(): string {
  const globalBase = (globalThis as unknown as { __VITE_API_BASE_URL__?: string }).__VITE_API_BASE_URL__;
  if (typeof globalBase === 'string') return globalBase;
  return (((import.meta as unknown as { env?: Record<string, unknown> })?.env?.VITE_API_BASE_URL as string) ?? '') || '';
}
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  const base = getApiBase();
  return base ? `${base}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

export type PostSummaryGenConfig = {
  enabled: boolean;
  model?: string | null;
  providerId?: string | null;
  temperature?: number | null;
  topP?: number | null;
  enableThinking?: boolean | null;
  maxContentChars: number;
  promptCode: string;
};

export type PostSummaryGenConfigDTO = PostSummaryGenConfig & {
  id?: number | null;
  version?: number | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
};

export type PostSummaryGenHistoryDTO = {
  id: number;
  postId: number;
  status: string;
  model?: string | null;
  createdAt: string;
  latencyMs?: number | null;
  errorMessage?: string | null;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  number: number;
  size: number;
};

export async function adminGetPostSummaryConfig(): Promise<PostSummaryGenConfigDTO> {
  const res = await fetch(apiUrl('api/admin/semantic/summary/config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取帖子摘要配置失败');
  return data as PostSummaryGenConfigDTO;
}

export async function adminUpsertPostSummaryConfig(payload: PostSummaryGenConfig): Promise<PostSummaryGenConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/semantic/summary/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存帖子摘要配置失败');
  return data as PostSummaryGenConfigDTO;
}

export async function adminListPostSummaryHistory(params?: {
  page?: number;
  size?: number;
  postId?: number;
}): Promise<Page<PostSummaryGenHistoryDTO>> {
  const qs = new URLSearchParams();
  qs.set('page', String(params?.page ?? 0));
  qs.set('size', String(params?.size ?? 20));
  if (params?.postId) qs.set('postId', String(params.postId));

  const res = await fetch(apiUrl(`/api/admin/semantic/summary/history?${qs.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取帖子摘要生成日志失败');
  return data as Page<PostSummaryGenHistoryDTO>;
}

export async function adminRegeneratePostSummary(postId: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/semantic/summary/regenerate'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ postId }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '提交重新生成失败');
}
