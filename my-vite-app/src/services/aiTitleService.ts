import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

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
