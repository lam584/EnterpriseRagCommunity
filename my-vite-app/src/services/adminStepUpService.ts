import { getCsrfToken } from '../utils/csrfUtils';

export type AdminStepUpStatusDTO = {
  ok: boolean;
  okUntilEpochMs?: number;
  ttlSeconds?: number;
  methods: string[];
  emailOtpAllowed?: boolean;
};

export async function getAdminStepUpStatus(): Promise<AdminStepUpStatusDTO> {
  const res = await fetch('/api/admin/step-up/status', { credentials: 'include' });
  const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) {
    const msg = typeof data?.message === 'string' ? data.message : undefined;
    throw new Error(msg || '获取 step-up 状态失败');
  }
  return {
    ok: Boolean(data.ok),
    okUntilEpochMs: typeof data.okUntilEpochMs === 'number' ? data.okUntilEpochMs : undefined,
    ttlSeconds: typeof data.ttlSeconds === 'number' ? data.ttlSeconds : undefined,
    methods: Array.isArray(data.methods) ? (data.methods as unknown[]).map(String) : [],
    emailOtpAllowed: typeof data.emailOtpAllowed === 'boolean' ? data.emailOtpAllowed : undefined,
  };
}

export async function verifyAdminStepUp(payload: { method: 'email' | 'totp'; code: string }): Promise<{ ok: boolean }> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/admin/step-up/verify', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) {
    const msg = typeof data?.message === 'string' ? data.message : undefined;
    throw new Error(msg || '二次验证失败');
  }
  return { ok: true };
}

export async function clearAdminStepUp(): Promise<void> {
  const csrfToken = await getCsrfToken();
  await fetch('/api/admin/step-up/clear', {
    method: 'POST',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
}

