import { getCsrfToken } from '../utils/csrfUtils';
import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';

const apiUrl = serviceApiUrl;

export type AiPostTagSuggestRequest = {
  title?: string;
  content: string;
  count?: number;
  model?: string;
  temperature?: number;
  topP?: number;
  boardName?: string;
  tags?: string[];
};

export type AiPostTagSuggestResponse = {
  tags: string[];
  model?: string;
  latencyMs?: number;
};


export async function suggestPostTags(payload: AiPostTagSuggestRequest): Promise<AiPostTagSuggestResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl('/api/ai/posts/tag-suggestions'), {
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
    throw new Error(getBackendMessage(data) || '生成标签失败');
  }

  return data as AiPostTagSuggestResponse;
}
