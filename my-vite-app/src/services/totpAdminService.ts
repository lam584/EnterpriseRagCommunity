import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';
import type { UserQueryDTO } from '../types/userAccess';

export type TotpAdminSettingsDTO = {
  issuer: string;
  allowedAlgorithms: string[];
  allowedDigits: number[];
  allowedPeriodSeconds: number[];
  maxSkew: number;
  defaultAlgorithm: string;
  defaultDigits: number;
  defaultPeriodSeconds: number;
  defaultSkew: number;
};

export type AdminUserTotpStatusDTO = {
  userId: number;
  email?: string;
  username?: string;
  enabled?: boolean;
  verifiedAt?: string;
  createdAt?: string;
  algorithm?: string;
  digits?: number;
  periodSeconds?: number;
  skew?: number;
};

const SETTINGS_API = '/api/admin/settings/totp';
const USERS_API = '/api/admin/users/totp';

export async function getTotpAdminSettings(): Promise<TotpAdminSettingsDTO> {
  const res = await fetch(SETTINGS_API, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载 TOTP 策略失败');
  }
  return res.json();
}

export async function updateTotpAdminSettings(dto: TotpAdminSettingsDTO): Promise<TotpAdminSettingsDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(SETTINGS_API, {
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
    throw new Error(msg || '保存 TOTP 策略失败');
  }
  return res.json();
}

export async function queryAdminUserTotpStatus(query: UserQueryDTO): Promise<SpringPage<AdminUserTotpStatusDTO>> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${USERS_API}/query`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(query),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载用户 TOTP 状态失败');
  }
  return res.json();
}

export async function resetAdminUserTotp(userId: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${USERS_API}/${userId}/reset`, {
    method: 'POST',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '重置用户 TOTP 失败');
  }
}

