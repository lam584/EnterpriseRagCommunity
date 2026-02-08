export type Security2faPolicyStatusDTO = {
  totpAllowed: boolean;
  totpRequired: boolean;
  totpCanDisable: boolean;
  emailOtpAllowed: boolean;
  emailOtpRequired: boolean;
  emailServiceEnabled: boolean;
};

const API = '/api/account/security-2fa-policy';

export async function getMySecurity2faPolicy(): Promise<Security2faPolicyStatusDTO> {
  const res = await fetch(API, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载 2FA 策略失败');
  }
  return res.json();
}

