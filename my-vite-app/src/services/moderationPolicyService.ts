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

export type ModerationPolicyContentType = 'POST' | 'COMMENT' | 'PROFILE';

export type ModerationPolicyConfig = {
  contentType: ModerationPolicyContentType;
  policyVersion: string;
  config: Record<string, unknown>;
};

export type ModerationPolicyConfigDTO = ModerationPolicyConfig & {
  id?: number;
  version?: number;
  updatedAt?: string;
  updatedBy?: string | null;
};

export async function adminGetModerationPolicyConfig(contentType: ModerationPolicyContentType): Promise<ModerationPolicyConfigDTO> {
  const res = await fetch(apiUrl(`/api/admin/moderation/policy/config?contentType=${encodeURIComponent(contentType)}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取审核策略配置失败');
  return data as ModerationPolicyConfigDTO;
}

export async function adminUpsertModerationPolicyConfig(payload: ModerationPolicyConfig): Promise<ModerationPolicyConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/moderation/policy/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存审核策略配置失败');
  return data as ModerationPolicyConfigDTO;
}

