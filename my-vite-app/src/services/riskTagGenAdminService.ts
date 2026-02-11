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

export type PostRiskTagGenConfig = {
  enabled: boolean;
  systemPrompt: string;
  promptTemplate: string;
  model?: string | null;
  providerId?: string | null;
  temperature?: number | null;
  topP?: number | null;
  enableThinking?: boolean | null;
  maxCount: number;
  maxContentChars: number;
};

export type PostRiskTagGenConfigDTO = PostRiskTagGenConfig & {
  id?: number | null;
  version?: number | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
};

export async function adminGetPostRiskTagGenConfig(): Promise<PostRiskTagGenConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/semantic/risk-tag/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取风险标签生成配置失败');
  return data as PostRiskTagGenConfigDTO;
}

export async function adminUpsertPostRiskTagGenConfig(payload: PostRiskTagGenConfig): Promise<PostRiskTagGenConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/semantic/risk-tag/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存风险标签生成配置失败');
  return data as PostRiskTagGenConfigDTO;
}
