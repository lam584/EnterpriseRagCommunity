import { getCsrfToken } from '../utils/csrfUtils';

export type Security2faPolicyStatusDTO = {
  totpAllowed: boolean;
  totpRequired: boolean;
  totpCanDisable: boolean;
  emailOtpAllowed: boolean;
  emailOtpRequired: boolean;
  emailServiceEnabled: boolean;
  login2faAllowed: boolean;
  login2faRequired: boolean;
  login2faCanEnable: boolean;
  login2faEnabled: boolean;
};

const API = '/api/account/security-2fa-policy';
const LOGIN_2FA_PREF_API = '/api/account/login-2fa-preference';
const LOGIN_2FA_PREF_VERIFY_PWD_API = '/api/account/login-2fa-preference/verify-password';

export type UpdateMyLogin2faPreferenceRequest = {
  enabled: boolean;
  method: 'totp' | 'email';
  totpCode?: string;
  emailCode?: string;
};

export async function getMySecurity2faPolicy(): Promise<Security2faPolicyStatusDTO> {
  const res = await fetch(API, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载 2FA 策略失败');
  }
  return res.json();
}

export async function verifyMyLogin2faPreferencePassword(password: string): Promise<{ message?: string }> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(LOGIN_2FA_PREF_VERIFY_PWD_API, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ password }),
  });
  const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) {
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '验证密码失败');
  }
  return { message: typeof data.message === 'string' ? data.message : undefined };
}

export async function updateMyLogin2faPreference(req: UpdateMyLogin2faPreferenceRequest): Promise<Security2faPolicyStatusDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(LOGIN_2FA_PREF_API, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '保存失败');
  }
  return res.json();
}
