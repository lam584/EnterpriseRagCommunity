import { getCsrfToken } from '../utils/csrfUtils';
import { getErrorMessage, parseSendCodeResponse } from './serviceResponseUtils';

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
    throw new Error(getErrorMessage(data, '发送验证码失败'));
  }
  return parseSendCodeResponse(data);
}
