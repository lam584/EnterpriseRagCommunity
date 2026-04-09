import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type PortalChatConfigDTO = {
  assistantChat?: {
    allowManualModelSelection?: boolean | null;
    providerId?: string | null;
    model?: string | null;
    temperature?: number | null;
    topP?: number | null;
    historyLimit?: number | null;
    defaultDeepThink?: boolean | null;
    defaultUseRag?: boolean | null;
    ragTopK?: number | null;
    defaultStream?: boolean | null;
    systemPromptCode?: string | null;
    deepThinkSystemPromptCode?: string | null;
  } | null;
  postComposeAssistant?: {
    allowManualModelSelection?: boolean | null;
    providerId?: string | null;
    model?: string | null;
    temperature?: number | null;
    topP?: number | null;
    chatHistoryLimit?: number | null;
    defaultDeepThink?: boolean | null;
    systemPromptCode?: string | null;
    deepThinkSystemPromptCode?: string | null;
    composeSystemPromptCode?: string | null;
  } | null;
};

export async function adminGetPortalChatConfig(): Promise<PortalChatConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/ai/portal-chat/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取前台对话配置失败');
  return data as PortalChatConfigDTO;
}

export async function adminUpsertPortalChatConfig(payload: PortalChatConfigDTO): Promise<PortalChatConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/portal-chat/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存前台对话配置失败');
  return data as PortalChatConfigDTO;
}

