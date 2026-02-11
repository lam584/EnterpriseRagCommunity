import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

export type AiPostTitleSuggestRequest = {
  content: string;
  count?: number;
  model?: string;
  temperature?: number;
  topP?: number;
  boardName?: string;
  tags?: string[];
};

export type AiPostTitleSuggestResponse = {
  titles: string[];
  model?: string;
  latencyMs?: number;
};

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

export async function suggestPostTitles(payload: AiPostTitleSuggestRequest): Promise<AiPostTitleSuggestResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl('/api/ai/posts/title-suggestions'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '生成标题失败');
  }

  return data as AiPostTitleSuggestResponse;
}

