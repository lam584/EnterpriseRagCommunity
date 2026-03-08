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

export type PromptContentDTO = {
  promptCode: string;
  name?: string | null;
  systemPrompt?: string | null;
  userPromptTemplate?: string | null;
  temperature?: number | null;
  topP?: number | null;
  maxTokens?: number | null;
  enableDeepThinking?: boolean | null;
  version?: number | null;
  updatedBy?: number | null;
};

export type PromptBatchResponse = {
  prompts: PromptContentDTO[];
  missingCodes: string[];
};

export async function adminBatchGetPrompts(codes: string[]): Promise<PromptBatchResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/prompts/batch'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ codes }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取提示词内容失败');
  return data as PromptBatchResponse;
}

export async function adminUpdatePromptContent(
  promptCode: string,
  payload: {
    name?: string | null;
    systemPrompt?: string | null;
    userPromptTemplate?: string | null;
    temperature?: number | null;
    topP?: number | null;
    maxTokens?: number | null;
    enableDeepThinking?: boolean | null;
  }
): Promise<PromptContentDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/prompts/${encodeURIComponent(promptCode)}/content`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存提示词内容失败');
  return data as PromptContentDTO;
}
