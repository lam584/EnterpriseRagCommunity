import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage, readJsonRecord, readNumberField } from './serviceErrorUtils';

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
    const data = await readJsonRecord(res);
    throw new Error(getBackendMessage(data) || '密码验证失败');
  }
}

export async function sendOldEmailVerificationCode(): Promise<{
  message?: string;
  resendWaitSeconds?: number;
  codeTtlSeconds?: number;
}> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/account/email-change/old/send-code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  const data = await readJsonRecord(res);
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '发送验证码失败');
  }

  return {
    message: getBackendMessage(data),
    resendWaitSeconds: readNumberField(data, 'resendWaitSeconds'),
    codeTtlSeconds: readNumberField(data, 'codeTtlSeconds'),
  };
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
    const data = await readJsonRecord(res);
    throw new Error(getBackendMessage(data) || '验证失败');
  }
}

export async function sendChangeEmailVerificationCode(newEmail: string): Promise<{
  message?: string;
  resendWaitSeconds?: number;
  codeTtlSeconds?: number;
}> {
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
  const data = await readJsonRecord(res);
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '发送验证码失败');
  }

  return {
    message: getBackendMessage(data),
    resendWaitSeconds: readNumberField(data, 'resendWaitSeconds'),
    codeTtlSeconds: readNumberField(data, 'codeTtlSeconds'),
  };
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
    const data = await readJsonRecord(res);
    throw new Error(getBackendMessage(data) || '更换邮箱失败');
  }
}
