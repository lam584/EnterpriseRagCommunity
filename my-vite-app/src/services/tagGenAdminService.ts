import { getCsrfToken } from '../utils/csrfUtils';

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

export type PostTagGenConfig = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model?: string | null;
  providerId?: string | null;
  temperature?: number | null;
  topP?: number | null;
  enableThinking?: boolean | null;
  defaultCount: number;
  maxCount: number;
  maxContentChars: number;
  historyEnabled: boolean;
  historyKeepDays?: number | null;
  historyKeepRows?: number | null;
};

export type PostTagGenConfigDTO = PostTagGenConfig & {
  id?: number | null;
  version?: number | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
};

export type PostTagGenHistoryDTO = {
  id: number;
  userId: number;
  createdAt: string;
  boardName?: string | null;
  titleExcerpt?: string | null;
  requestedCount: number;
  appliedMaxContentChars: number;
  contentLen: number;
  contentExcerpt?: string | null;
  tags: string[];
  model?: string | null;
  temperature?: number | null;
  topP?: number | null;
  latencyMs?: number | null;
  promptVersion?: number | null;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  number: number;
  size: number;
};

export async function adminGetPostTagGenConfig(): Promise<PostTagGenConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/semantic/multi-label/config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取主题标签生成配置失败');
  return data as PostTagGenConfigDTO;
}

export async function adminUpsertPostTagGenConfig(payload: PostTagGenConfig): Promise<PostTagGenConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/semantic/multi-label/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存主题标签生成配置失败');
  return data as PostTagGenConfigDTO;
}

export async function adminListPostTagGenHistory(params?: {
  page?: number;
  size?: number;
  userId?: number;
}): Promise<Page<PostTagGenHistoryDTO>> {
  const qs = new URLSearchParams();
  qs.set('page', String(params?.page ?? 0));
  qs.set('size', String(params?.size ?? 20));
  if (params?.userId) qs.set('userId', String(params.userId));

  const res = await fetch(apiUrl(`/api/admin/semantic/multi-label/history?${qs.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取主题标签生成历史失败');
  return data as Page<PostTagGenHistoryDTO>;
}
