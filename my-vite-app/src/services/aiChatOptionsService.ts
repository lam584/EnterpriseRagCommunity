import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
const apiUrl = serviceApiUrl;


export type AiChatModelOptionDTO = {
  name?: string | null;
  isDefault?: boolean | null;
};

export type AiChatProviderOptionDTO = {
  id?: string | null;
  name?: string | null;
  defaultChatModel?: string | null;
  chatModels?: AiChatModelOptionDTO[] | null;
};

export type AiChatOptionsDTO = {
  activeProviderId?: string | null;
  providers?: AiChatProviderOptionDTO[] | null;
  assistantManualModelSelectionEnabled?: boolean | null;
  postComposeManualModelSelectionEnabled?: boolean | null;
};

export async function getAiChatOptions(): Promise<AiChatOptionsDTO> {
  const res = await fetch(apiUrl('/api/ai/chat/options'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取模型选项失败');
  return data as AiChatOptionsDTO;
}

