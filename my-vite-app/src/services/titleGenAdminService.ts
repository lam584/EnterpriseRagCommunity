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

export type PostTitleGenConfig = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model?: string | null;
  providerId?: string | null;
  temperature?: number | null;
  defaultCount: number;
  maxCount: number;
  maxContentChars: number;
  historyEnabled: boolean;
  historyKeepDays?: number | null;
  historyKeepRows?: number | null;
};

export type PostTitleGenConfigDTO = PostTitleGenConfig & {
  id?: number | null;
  version?: number | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
};

export type PostTitleGenHistoryDTO = {
  id: number;
  userId: number;
  createdAt: string;
  boardName?: string | null;
  tags?: string[] | null;
  requestedCount: number;
  appliedMaxContentChars: number;
  contentLen: number;
  contentExcerpt?: string | null;
  titles: string[];
  model?: string | null;
  temperature?: number | null;
  latencyMs?: number | null;
  promptVersion?: number | null;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  number: number;
  size: number;
};

export async function adminGetPostTitleGenConfig(): Promise<PostTitleGenConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/semantic/title-gen/config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取标题生成配置失败');
  return data as PostTitleGenConfigDTO;
}

export async function adminUpsertPostTitleGenConfig(payload: PostTitleGenConfig): Promise<PostTitleGenConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/semantic/title-gen/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存标题生成配置失败');
  return data as PostTitleGenConfigDTO;
}

export async function adminListPostTitleGenHistory(params?: {
  page?: number;
  size?: number;
  userId?: number;
}): Promise<Page<PostTitleGenHistoryDTO>> {
  const qs = new URLSearchParams();
  qs.set('page', String(params?.page ?? 0));
  qs.set('size', String(params?.size ?? 20));
  if (params?.userId) qs.set('userId', String(params.userId));

  const res = await fetch(apiUrl(`/api/admin/semantic/title-gen/history?${qs.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取标题生成历史失败');
  return data as Page<PostTitleGenHistoryDTO>;
}
