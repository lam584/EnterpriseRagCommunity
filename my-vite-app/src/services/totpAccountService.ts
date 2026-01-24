import { getCsrfToken } from '../utils/csrfUtils';
import type { TotpAdminSettingsDTO } from './totpAdminService';

export type TotpStatusResponse = {
  masterKeyConfigured?: boolean;
  enabled?: boolean;
  verifiedAt?: string;
  createdAt?: string;
  algorithm?: string;
  digits?: number;
  periodSeconds?: number;
  skew?: number;
};

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function extractApiErrorMessage(data: unknown): string | undefined {
  if (!data) return undefined;

  if (isPlainObject(data)) {
    const message = typeof data.message === 'string' ? data.message : undefined;
    if (message) return message;

    const error = typeof data.error === 'string' ? data.error : undefined;
    if (error) return error;

    const detail = typeof data.detail === 'string' ? data.detail : undefined;
    if (detail) return detail;

    const stringEntries = Object.entries(data).filter(([, v]) => typeof v === 'string') as Array<[string, string]>;
    if (stringEntries.length === 1) return stringEntries[0]?.[1];
    if (stringEntries.length > 1) return stringEntries.map(([k, v]) => `${k}: ${v}`).join('；');
  }

  if (Array.isArray(data)) {
    const strings = data.filter((v): v is string => typeof v === 'string');
    if (strings.length === 1) return strings[0];
    if (strings.length > 1) return strings.join('；');
  }

  if (typeof data === 'string') return data;
  return undefined;
}

export type TotpEnrollRequest = {
  algorithm?: string;
  digits?: number;
  periodSeconds?: number;
  skew?: number;
};

export type TotpEnrollResponse = {
  otpauthUri: string;
  secretBase32: string;
  algorithm: string;
  digits: number;
  periodSeconds: number;
  skew: number;
};

const API_BASE = '/api/account/totp';

export async function getTotpPolicy(): Promise<TotpAdminSettingsDTO> {
  const res = await fetch(`${API_BASE}/policy`, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    throw new Error(extractApiErrorMessage(data) || '加载 TOTP 策略失败');
  }
  return res.json();
}

export async function getTotpStatus(): Promise<TotpStatusResponse> {
  const res = await fetch(`${API_BASE}/status`, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    throw new Error(extractApiErrorMessage(data) || '加载 TOTP 状态失败');
  }
  return res.json();
}

export async function enrollTotp(req?: TotpEnrollRequest): Promise<TotpEnrollResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE}/enroll`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(req ?? {}),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    throw new Error(extractApiErrorMessage(data) || '生成密钥失败');
  }
  return res.json();
}

export async function verifyTotp(code: string, password: string, emailCode?: string): Promise<TotpStatusResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE}/verify`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ code, password, emailCode }),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    throw new Error(extractApiErrorMessage(data) || '验证失败');
  }
  return res.json();
}

export async function disableTotp(code: string): Promise<TotpStatusResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE}/disable`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ code }),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    throw new Error(extractApiErrorMessage(data) || '停用失败');
  }
  return res.json();
}
