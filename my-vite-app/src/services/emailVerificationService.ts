import { getCsrfToken } from '../utils/csrfUtils';

export async function sendAccountEmailVerificationCode(purpose: string): Promise<{
  message?: string;
  resendWaitSeconds?: number;
  codeTtlSeconds?: number;
}> {
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
  const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) {
    const msg = typeof data?.message === 'string' ? data.message : undefined;
    throw new Error(msg || '发送验证码失败');
  }

  return {
    message: typeof data?.message === 'string' ? data.message : undefined,
    resendWaitSeconds: typeof data?.resendWaitSeconds === 'number' ? data.resendWaitSeconds : undefined,
    codeTtlSeconds: typeof data?.codeTtlSeconds === 'number' ? data.codeTtlSeconds : undefined,
  };
}
