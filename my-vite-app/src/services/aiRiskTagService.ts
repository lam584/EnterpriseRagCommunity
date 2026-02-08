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

export type AiPostRiskTagSuggestRequest = {
  title?: string;
  content: string;
  count?: number;
};

export type AiPostRiskTagSuggestResponse = {
  riskTags: string[];
  model?: string;
  latencyMs?: number;
};

export async function suggestPostRiskTags(payload: AiPostRiskTagSuggestRequest): Promise<AiPostRiskTagSuggestResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl('/api/ai/posts/risk-tag-suggestions'), {
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
    throw new Error(getBackendMessage(data) || '生成风险标签失败');
  }

  return data as AiPostRiskTagSuggestResponse;
}
