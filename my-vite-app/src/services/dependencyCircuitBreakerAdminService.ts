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

export type DependencyCircuitBreakerConfigDTO = {
  dependency?: string | null;
  failureThreshold?: number | null;
  cooldownSeconds?: number | null;
};

export async function adminGetDependencyCircuitBreakerConfig(dependency: string): Promise<DependencyCircuitBreakerConfigDTO> {
  const dep = String(dependency || '').trim();
  const res = await fetch(apiUrl(`/api/admin/safety/dependency-circuit-breakers/${encodeURIComponent(dep)}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载依赖熔断配置失败');
  return data as DependencyCircuitBreakerConfigDTO;
}

export async function adminUpdateDependencyCircuitBreakerConfig(dependency: string, payload: DependencyCircuitBreakerConfigDTO, reason: string): Promise<DependencyCircuitBreakerConfigDTO> {
  const dep = String(dependency || '').trim();
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/safety/dependency-circuit-breakers/${encodeURIComponent(dep)}`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ config: payload ?? {}, reason: reason ?? '' }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存依赖熔断配置失败');
  return data as DependencyCircuitBreakerConfigDTO;
}

