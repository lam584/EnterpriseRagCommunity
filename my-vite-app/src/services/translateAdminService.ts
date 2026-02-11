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

export type SemanticTranslateConfig = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model?: string | null;
  providerId?: string | null;
  temperature?: number | null;
  topP?: number | null;
  enableThinking?: boolean | null;
  maxContentChars: number;
  historyEnabled: boolean;
  historyKeepDays?: number | null;
  historyKeepRows?: number | null;
  allowedTargetLanguages?: string[] | null;
};

export type SemanticTranslateConfigDTO = SemanticTranslateConfig & {
  id?: number | null;
  version?: number | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
};

export type SemanticTranslateHistoryDTO = {
  id: number;
  userId: number;
  createdAt: string;
  sourceType: string;
  sourceId: number;
  targetLang: string;
  sourceTitleExcerpt?: string | null;
  sourceContentExcerpt?: string | null;
  translatedTitle?: string | null;
  translatedMarkdown: string;
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

export async function adminGetTranslateConfig(): Promise<SemanticTranslateConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/semantic/translate/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取翻译配置失败');
  return data as SemanticTranslateConfigDTO;
}

export async function adminUpsertTranslateConfig(payload: SemanticTranslateConfig): Promise<SemanticTranslateConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/semantic/translate/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存翻译配置失败');
  return data as SemanticTranslateConfigDTO;
}

export async function adminListTranslateHistory(params?: {
  page?: number;
  size?: number;
  userId?: number;
}): Promise<Page<SemanticTranslateHistoryDTO>> {
  const qs = new URLSearchParams();
  qs.set('page', String(params?.page ?? 0));
  qs.set('size', String(params?.size ?? 20));
  if (params?.userId) qs.set('userId', String(params.userId));

  const res = await fetch(apiUrl(`/api/admin/semantic/translate/history?${qs.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取翻译历史失败');
  return data as Page<SemanticTranslateHistoryDTO>;
}
