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

export type PostLangLabelGenConfig = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model?: string | null;
  temperature?: number | null;
  maxContentChars: number;
};

export type PostLangLabelGenConfigDTO = PostLangLabelGenConfig & {
  id?: number | null;
  version?: number | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
};

export async function adminGetPostLangLabelGenConfig(): Promise<PostLangLabelGenConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/semantic/lang-label/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取语言标签生成配置失败');
  return data as PostLangLabelGenConfigDTO;
}

export async function adminUpsertPostLangLabelGenConfig(payload: PostLangLabelGenConfig): Promise<PostLangLabelGenConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/semantic/lang-label/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存语言标签生成配置失败');
  return data as PostLangLabelGenConfigDTO;
}

