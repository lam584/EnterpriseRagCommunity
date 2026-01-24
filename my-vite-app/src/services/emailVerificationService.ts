import { getCsrfToken } from '../utils/csrfUtils';

export async function sendAccountEmailVerificationCode(purpose: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/account/email-verification/send', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ purpose }),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '发送验证码失败');
  }
}

