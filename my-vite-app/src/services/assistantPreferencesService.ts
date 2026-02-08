import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

export type AssistantPreferencesDTO = {
  defaultProviderId: string | null;
  defaultModel: string | null;
  defaultDeepThink: boolean;
  autoLoadLastSession: boolean;
  defaultUseRag: boolean;
  ragTopK: number;
  stream: boolean;
  temperature?: number | null;
  topP?: number | null;
  defaultSystemPrompt?: string | null;
};

function getBackendMessage(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const d = data as Record<string, unknown>;
  const msg = d.message ?? d.error;
  return msg == null ? null : String(msg);
}

export async function getMyAssistantPreferences(): Promise<AssistantPreferencesDTO> {
  const res = await fetch(apiUrl('/api/ai/assistant/preferences'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '获取助手偏好失败');
  }
  return data as AssistantPreferencesDTO;
}

export async function updateMyAssistantPreferences(
  payload: Partial<AssistantPreferencesDTO>
): Promise<AssistantPreferencesDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/ai/assistant/preferences'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '保存助手偏好失败');
  }
  return data as AssistantPreferencesDTO;
}
