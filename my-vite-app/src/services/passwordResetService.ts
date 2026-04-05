import { getCsrfToken } from '../utils/csrfUtils';
import { getErrorMessage, parseSendCodeResponse } from './serviceResponseUtils';

export interface PasswordResetStatusResponse {
  allowed: boolean;
  totpEnabled: boolean;
  emailEnabled: boolean;
  message?: string | null;
}

export async function getPasswordResetStatus(email: string): Promise<PasswordResetStatusResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/auth/password-reset/status', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ email }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data && data.message) || '查询找回密码状态失败');
  }
  return {
    allowed: Boolean((data as { allowed?: unknown }).allowed),
    totpEnabled: Boolean((data as { totpEnabled?: unknown }).totpEnabled),
    emailEnabled: Boolean((data as { emailEnabled?: unknown }).emailEnabled),
    message: (data as { message?: string | null }).message ?? null,
  };
}

export async function resetPasswordByTotp(email: string, totpCode: string, newPassword: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/auth/password-reset/reset', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ email, totpCode, newPassword }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data && data.message) || '重置密码失败');
  }
}

export async function sendPasswordResetEmailCode(email: string): Promise<{
  message?: string;
  resendWaitSeconds?: number;
  codeTtlSeconds?: number;
}> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/auth/password-reset/send-code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ email }),
  });

  const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) {
    throw new Error(getErrorMessage(data, '发送验证码失败'));
  }
  return parseSendCodeResponse(data);
}

export async function resetPasswordByEmailCode(email: string, emailCode: string, newPassword: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/auth/password-reset/reset', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ email, emailCode, newPassword }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data && data.message) || '重置密码失败');
  }
}
