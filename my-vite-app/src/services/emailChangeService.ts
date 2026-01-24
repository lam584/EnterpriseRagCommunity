import { getCsrfToken } from '../utils/csrfUtils';

export async function verifyEmailChangePassword(password: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/account/email-change/verify-password', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ password }),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '密码验证失败');
  }
}

export async function sendOldEmailVerificationCode(): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/account/email-change/old/send-code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '发送验证码失败');
  }
}

export type VerifyOldMethod = 'email' | 'totp';

export async function verifyOldEmailOrTotp(body: {
  method: VerifyOldMethod;
  emailCode?: string;
  totpCode?: string;
}): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/account/email-change/old/verify', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '验证失败');
  }
}

export async function sendChangeEmailVerificationCode(newEmail: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/account/email-change/send-code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ newEmail }),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '发送验证码失败');
  }
}

export type ChangeEmailRequest = {
  newEmail: string;
  newEmailCode: string;
};

export async function changeEmail(body: ChangeEmailRequest): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/account/email-change', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '更换邮箱失败');
  }
}
