import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

export type ContentSafetyCircuitBreakerScopeDTO = {
  all?: boolean | null;
  userIds?: number[] | null;
  postIds?: number[] | null;
  entrypoints?: string[] | null;
};

export type ContentSafetyCircuitBreakerDependencyIsolationDTO = {
  mysql?: boolean | null;
  elasticsearch?: boolean | null;
};

export type ContentSafetyCircuitBreakerAutoTriggerDTO = {
  enabled?: boolean | null;
  windowSeconds?: number | null;
  thresholdCount?: number | null;
  minConfidence?: number | null;
  verdicts?: string[] | null;
  triggerMode?: string | null;
  coolDownSeconds?: number | null;
  autoRecoverSeconds?: number | null;
};

export type ContentSafetyCircuitBreakerConfigDTO = {
  enabled?: boolean | null;
  mode?: string | null;
  message?: string | null;
  scope?: ContentSafetyCircuitBreakerScopeDTO | null;
  dependencyIsolation?: ContentSafetyCircuitBreakerDependencyIsolationDTO | null;
  autoTrigger?: ContentSafetyCircuitBreakerAutoTriggerDTO | null;
};

export type ContentSafetyCircuitBreakerEventDTO = {
  at?: string | null;
  type?: string | null;
  message?: string | null;
  details?: Record<string, unknown> | null;
};

export type ContentSafetyCircuitBreakerStatusDTO = {
  config?: ContentSafetyCircuitBreakerConfigDTO | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
  updatedByUserId?: number | null;
  persisted?: boolean | null;
  lastPersistAt?: string | null;
  runtimeMetrics?: {
    blockedTotal?: number | null;
    blockedLast60s?: number | null;
    blockedByEntrypoint?: Record<string, number> | null;
  } | null;
  recentEvents?: ContentSafetyCircuitBreakerEventDTO[] | null;
};

export async function adminGetContentSafetyCircuitBreakerStatus(): Promise<ContentSafetyCircuitBreakerStatusDTO> {
  const res = await fetch(apiUrl('/api/admin/safety/circuit-breaker/status'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载熔断状态失败');
  return data as ContentSafetyCircuitBreakerStatusDTO;
}

export async function adminUpdateContentSafetyCircuitBreakerConfig(payload: ContentSafetyCircuitBreakerConfigDTO, reason: string): Promise<ContentSafetyCircuitBreakerStatusDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/safety/circuit-breaker/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ config: payload ?? {}, reason: reason ?? '' }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存熔断配置失败');
  return data as ContentSafetyCircuitBreakerStatusDTO;
}
