import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';

const API_BASE: string = ((import.meta as unknown as { env?: Record<string, unknown> })?.env?.VITE_API_BASE_URL as string) ?? '';

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

export type ContextWindowPolicy =
  | 'FIXED'
  | 'ADAPTIVE'
  | 'SLIDING'
  | 'TOPK'
  | 'IMPORTANCE'
  | 'DEDUP'
  | 'HYBRID'
  | string;

export type ContextClipConfigDTO = {
  enabled?: boolean | null;
  policy?: ContextWindowPolicy | null;

  maxItems?: number | null;
  maxContextTokens?: number | null;
  reserveAnswerTokens?: number | null;
  perItemMaxTokens?: number | null;
  maxPromptChars?: number | null;

  minScore?: number | null;
  maxSamePostItems?: number | null;
  requireTitle?: boolean | null;

  dedupByPostId?: boolean | null;
  dedupByTitle?: boolean | null;
  dedupByContentHash?: boolean | null;

  sectionTitle?: string | null;
  itemHeaderTemplate?: string | null;
  separator?: string | null;

  showPostId?: boolean | null;
  showChunkIndex?: boolean | null;
  showScore?: boolean | null;
  showTitle?: boolean | null;

  extraInstruction?: string | null;

  logEnabled?: boolean | null;
  logSampleRate?: number | null;
  logMaxDays?: number | null;
};

export type ContextClipTestRequest = {
  queryText: string;
  boardId?: number | null;
  debug?: boolean | null;
  useSavedConfig?: boolean | null;
  config?: ContextClipConfigDTO | null;
};

export type ContextClipTestItem = {
  rank?: number | null;
  postId?: number | null;
  chunkIndex?: number | null;
  score?: number | null;
  title?: string | null;
  tokens?: number | null;
  reason?: string | null;
};

export type ContextClipTestResponse = {
  queryText?: string | null;
  boardId?: number | null;
  config?: ContextClipConfigDTO | null;
  budgetTokens?: number | null;
  usedTokens?: number | null;
  itemsSelected?: number | null;
  itemsDropped?: number | null;
  contextPrompt?: string | null;
  selected?: ContextClipTestItem[] | null;
  dropped?: ContextClipTestItem[] | null;
};

export type ContextWindowLogDTO = {
  id: number;
  eventId?: number | null;
  policy?: ContextWindowPolicy | null;
  totalTokens?: number | null;
  items?: number | null;
  queryText?: string | null;
  createdAt?: string | null;
};

export type ContextWindowDetailDTO = {
  id: number;
  eventId?: number | null;
  queryText?: string | null;
  policy?: ContextWindowPolicy | null;
  totalTokens?: number | null;
  chunkIds?: Record<string, unknown> | null;
  createdAt?: string | null;
};

export async function adminGetContextClipConfig(): Promise<ContextClipConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/retrieval/context/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取动态上下文裁剪配置失败');
  return data as ContextClipConfigDTO;
}

export async function adminUpdateContextClipConfig(payload: ContextClipConfigDTO): Promise<ContextClipConfigDTO> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/context/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存动态上下文裁剪配置失败');
  return data as ContextClipConfigDTO;
}

export async function adminTestContextClip(payload: ContextClipTestRequest): Promise<ContextClipTestResponse> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/context/test'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '动态上下文裁剪测试失败');
  return data as ContextClipTestResponse;
}

export async function adminListContextWindows(params?: {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
}): Promise<SpringPage<ContextWindowLogDTO>> {
  const sp = new URLSearchParams();
  sp.set('page', String(params?.page ?? 0));
  sp.set('size', String(params?.size ?? 20));
  if (params?.from) sp.set('from', params.from);
  if (params?.to) sp.set('to', params.to);

  const res = await fetch(apiUrl(`/api/admin/retrieval/context/logs/windows?${sp.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取上下文窗口日志失败');
  return data as SpringPage<ContextWindowLogDTO>;
}

export async function adminGetContextWindow(id: number): Promise<ContextWindowDetailDTO> {
  const res = await fetch(apiUrl(`/api/admin/retrieval/context/logs/windows/${id}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取上下文窗口详情失败');
  return data as ContextWindowDetailDTO;
}

