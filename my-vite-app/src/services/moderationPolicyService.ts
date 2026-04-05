import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

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
