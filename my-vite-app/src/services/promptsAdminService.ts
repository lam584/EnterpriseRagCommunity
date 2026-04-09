import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type PromptContentDTO = {
  promptCode: string;
  name?: string | null;
  systemPrompt?: string | null;
  userPromptTemplate?: string | null;
  visionProviderId?: string | null;
  visionModel?: string | null;
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
    visionProviderId?: string | null;
    visionModel?: string | null;
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
