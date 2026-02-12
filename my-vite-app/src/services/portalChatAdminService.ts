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
  if (data && typeof data === 'object' && 'error' in data && typeof (data as { error?: unknown }).error === 'string') {
    return (data as { error: string }).error;
  }
  return undefined;
}

export type PortalChatConfigDTO = {
  assistantChat?: {
    providerId?: string | null;
    model?: string | null;
    temperature?: number | null;
    topP?: number | null;
    historyLimit?: number | null;
    defaultDeepThink?: boolean | null;
    defaultUseRag?: boolean | null;
    ragTopK?: number | null;
    defaultStream?: boolean | null;
    systemPrompt?: string | null;
    deepThinkSystemPrompt?: string | null;
  } | null;
  postComposeAssistant?: {
    providerId?: string | null;
    model?: string | null;
    temperature?: number | null;
    topP?: number | null;
    chatHistoryLimit?: number | null;
    defaultDeepThink?: boolean | null;
    systemPrompt?: string | null;
    deepThinkSystemPrompt?: string | null;
    composeSystemPrompt?: string | null;
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

