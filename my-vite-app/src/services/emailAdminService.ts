import { getCsrfToken } from '../utils/csrfUtils';

export type EmailAdminSettingsDTO = {
  enabled?: boolean;
  otpTtlSeconds?: number;
  otpResendWaitSeconds?: number;
  otpResendWaitReductionSecondsAfterVerified?: number;
  protocol?: string;
  host?: string;
  portPlain?: number;
  portEncrypted?: number;
  encryption?: string;
  connectTimeoutMs?: number;
  timeoutMs?: number;
  writeTimeoutMs?: number;
  debug?: boolean;
  sslTrust?: string;
  subjectPrefix?: string;
  username?: string;
  password?: string;
  from?: string;
  fromName?: string;
};

const SETTINGS_API = '/api/admin/settings/email';
const TEST_API = '/api/admin/settings/email/test';
const INBOX_SETTINGS_API = '/api/admin/settings/email/inbox-config';
const INBOX_LIST_API = '/api/admin/settings/email/inbox';
const SENT_LIST_API = '/api/admin/settings/email/sent';

export type EmailInboxSettingsDTO = {
  protocol?: string;
  host?: string;
  portPlain?: number;
  portEncrypted?: number;
  encryption?: string;
  connectTimeoutMs?: number;
  timeoutMs?: number;
  writeTimeoutMs?: number;
  debug?: boolean;
  sslTrust?: string;
  folder?: string;
  sentFolder?: string;
};

export type EmailInboxMessageDTO = {
  id: string;
  subject?: string;
  from?: string;
  to?: string;
  sentAt?: number | null;
  content?: string;
};

export async function getEmailAdminSettings(): Promise<EmailAdminSettingsDTO> {
  const res = await fetch(SETTINGS_API, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载邮箱配置失败');
  }
  return res.json();
}

export async function updateEmailAdminSettings(dto: EmailAdminSettingsDTO): Promise<EmailAdminSettingsDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(SETTINGS_API, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(dto),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '保存邮箱配置失败');
  }
  return res.json();
}

export async function sendEmailAdminTest(to: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(TEST_API, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ to }),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '发送测试邮件失败');
  }
}

export async function getEmailInboxAdminSettings(): Promise<EmailInboxSettingsDTO> {
  const res = await fetch(INBOX_SETTINGS_API, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载收件配置失败');
  }
  return res.json();
}

export async function updateEmailInboxAdminSettings(dto: EmailInboxSettingsDTO): Promise<EmailInboxSettingsDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(INBOX_SETTINGS_API, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(dto),
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '保存收件配置失败');
  }
  return res.json();
}

export async function listEmailInboxMessages(limit = 20): Promise<EmailInboxMessageDTO[]> {
  const url = `${INBOX_LIST_API}?limit=${encodeURIComponent(String(limit))}`;
  const res = await fetch(url, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载收件箱失败');
  }
  return res.json();
}

export async function listEmailSentMessages(limit = 20): Promise<EmailInboxMessageDTO[]> {
  const url = `${SENT_LIST_API}?limit=${encodeURIComponent(String(limit))}`;
  const res = await fetch(url, { method: 'GET', credentials: 'include' });
  if (!res.ok) {
    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const msg = typeof data.message === 'string' ? data.message : undefined;
    throw new Error(msg || '加载发件箱失败');
  }
  return res.json();
}
