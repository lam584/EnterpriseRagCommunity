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

export async function getMySecurity2faPolicy(): Promise<Security2faPolicyStatusDTO> {
  const res = await fetch(API, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载 2FA 策略失败');
  }
  return res.json();
}

export async function updateMyLogin2faPreference(enabled: boolean): Promise<Security2faPolicyStatusDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(LOGIN_2FA_PREF_API, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ enabled }),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '保存失败');
  }
  return res.json();
}
