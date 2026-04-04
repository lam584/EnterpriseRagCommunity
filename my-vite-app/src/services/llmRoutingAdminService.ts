import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';

function getApiBase(): string {
  const globalBase = (globalThis as unknown as { __VITE_API_BASE_URL__?: string }).__VITE_API_BASE_URL__;
  if (typeof globalBase === 'string') return globalBase;
  return (((import.meta as unknown as { env?: Record<string, unknown> })?.env?.VITE_API_BASE_URL as string) ?? '') || '';
}
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  const base = getApiBase();
  return base ? `${base}${path}` : path;
}

export type AdminLlmRoutingScenarioDTO = {
  taskType: string;
  label: string;
  category: string;
  sortIndex: number;
};

export type AdminLlmRoutingPolicyDTO = {
  taskType: string;
  strategy?: string | null;
  maxAttempts?: number | null;
  failureThreshold?: number | null;
  cooldownMs?: number | null;
};

export type AdminLlmRoutingTargetDTO = {
  taskType: string;
  providerId: string;
  modelName: string;
  enabled?: boolean | null;
  weight?: number | null;
  priority?: number | null;
  sortIndex?: number | null;
  minDelayMs?: number | null;
  qps?: number | null;
  priceConfigId?: number | null;
};

export type AdminLlmRoutingConfigDTO = {
  scenarios?: AdminLlmRoutingScenarioDTO[] | null;
  policies?: AdminLlmRoutingPolicyDTO[] | null;
  targets?: AdminLlmRoutingTargetDTO[] | null;
};

export async function adminGetLlmRoutingConfig(): Promise<AdminLlmRoutingConfigDTO> {
  const res = await fetch(apiUrl('api/admin/ai/routing/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取负载均衡配置失败');
  return data as AdminLlmRoutingConfigDTO;
}

export async function adminUpdateLlmRoutingConfig(payload: AdminLlmRoutingConfigDTO): Promise<AdminLlmRoutingConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/routing/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg = getBackendMessage(data) || '保存负载均衡配置失败';
    if (res.status === 409) throw new Error(`${msg}（请先点击“刷新”再保存）`);
    throw new Error(msg);
  }
  return data as AdminLlmRoutingConfigDTO;
}
