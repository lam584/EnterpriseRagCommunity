import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type LangLabelGenPublicConfigDTO = {
  enabled?: boolean;
  maxContentChars?: number;
};

export async function getLangLabelGenConfig(): Promise<LangLabelGenPublicConfigDTO> {
  const res = await fetch(apiUrl('/api/ai/posts/lang-label-gen/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取语言标签配置失败');
  return data as LangLabelGenPublicConfigDTO;
}

export type AiPostLangLabelSuggestRequest = {
  title?: string;
  content?: string;
};

export type AiPostLangLabelSuggestResponse = {
  languages: string[];
  model?: string;
  latencyMs?: number;
};

export async function suggestPostLangLabels(payload: AiPostLangLabelSuggestRequest): Promise<AiPostLangLabelSuggestResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/ai/posts/lang-label-suggestions'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '生成语言标签失败');
  return data as AiPostLangLabelSuggestResponse;
}
