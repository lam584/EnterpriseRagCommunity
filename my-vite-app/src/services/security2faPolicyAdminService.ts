import { getCsrfToken } from '../utils/csrfUtils';

export type Security2faPolicySettingsDTO = {
  totpPolicy?: string;
  totpRoleIds?: number[];
  emailOtpPolicy?: string;
  emailOtpRoleIds?: number[];
};

const API = '/api/admin/settings/security-2fa-policy';

export async function getSecurity2faPolicySettings(): Promise<Security2faPolicySettingsDTO> {
  const res = await fetch(API, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载 2FA 启用策略失败');
  }
  return res.json();
}

export async function updateSecurity2faPolicySettings(dto: Security2faPolicySettingsDTO): Promise<Security2faPolicySettingsDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(API, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(dto),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '保存 2FA 启用策略失败');
  }
  return res.json();
}

