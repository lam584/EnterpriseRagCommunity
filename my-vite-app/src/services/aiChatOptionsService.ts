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

